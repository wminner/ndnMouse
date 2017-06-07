#!/usr/bin/env python3

import sys, getopt, time
import pyndn, ipaddress
import subprocess
import pyautogui

import logging
import pickle

from Crypto import Random
from Crypto.Cipher import AES
import hashlib

from datetime import datetime

def main(argv):
	logging_filename = "ndnMouse-log.txt"
	logging_level = logging.ERROR
	default_address = "192.168.1.2"
	
	def printUsage():
		print("Usage: ndnMouse-client-udp.py [logging_level_flag]")
		print("No flag specified: logging level defaults to errors only")
		print("  -h  help/print usage")
		print("  -d  logging level debug")
		print("  -i  logging level info")
		print("  -n  logging level none")

	# Parse arguments
	if len(sys.argv) != 0:
		try:
			opts, args = getopt.getopt(argv, "dhin")
		except getopt.GetoptError:
			printUsage()
			sys.exit(2)

		for opt, arg in opts:
			if opt == '-h':
				printUsage()
				sys.exit()
			elif opt in ('-d'):
				logging_level = logging.DEBUG
			elif opt in ('-i'):
				logging_level = logging.INFO
			elif opt in ('-n'):
				logging_level = logging.CRITICAL  # critical level never used

	# Set logging level based on flag provided (default to error only)
	logging.basicConfig(filename=logging_filename, level=logging_level)

	# Prompt user for server address (port is always 6363 for NFD)
	server_address = getServerAddress(default_address)

	# Prompt user for password
	password = getPassword()

	# Create a route from this PC's NFD to phone's NFD
	if NFDIsRunning():
		if not setupNFD(server_address):
			print("Error: could not set up NFD route!\nExiting...")
			logging.info("{0} Error: could not set up NFD route!\nExiting...".format(datetime.now()))
			exit(1)
	else:
		print("Error: NFD is not running!\nRun \"nfd-start\" and try again.\nExiting...")
		logging.info("{0} Error: NFD is not running!\nRun \"nfd-start\" and try again.\nExiting...".format(datetime.now()))
		exit(1)

	# Create server and run it
	if not password:
		server = ndnMouseClientNDN(server_address)
	else:
		server = ndnMouseClientNDNSecure(server_address, password)

	try:
		server.run()
	except KeyboardInterrupt:
		print("\nExiting...")
		logging.info("{0} Exiting...".format(datetime.now()))
	finally:
		server.shutdown()


################################################################################
# Class ndnMouseClientNDN
################################################################################

class ndnMouseClientNDN():

	# pyautogui variables
	transition_time = 0
	pyautogui.FAILSAFE = False
	pyautogui.PAUSE = 0

	# NDN variables
	interest_timeout = 50
	sleep_time = 0.020


	def __init__(self, addr):
		# Create face to work with NFD
		self.face = pyndn.face.Face()
		self.server_address = addr


	def run(self):
		print("Use ctrl+c quit at anytime....")
		print("Routing /ndnmouse interests to Face udp://{0}.".format(self.server_address))
		logging.info("{0} Use ctrl+c quit at anytime....".format(datetime.now()))
		logging.info("{0} Routing /ndnmouse interests to Face udp://{1}.".format(datetime.now(), self.server_address))

		# Make interest to get movement data
		interest_move = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/update"))
		interest_move.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_move.setMustBeFresh(True)

		# Send interests
		self.face.expressInterest(interest_move, self._onData, self._onTimeout)
		
		# Loop forever, processing data as it comes back
		# Additional interests are sent by _onData and _onTimeout callbacks
		while True:			
			self.face.processEvents()
			time.sleep(self.sleep_time)


	def shutdown(self):
		self.face.shutdown()


	############################################################################
	# Interest Callbacks
	############################################################################

	# Callback for when data is returned for an interest
	def _onData(self, interest, data):
		msg = bytes(data.getContent().buf())
		try:			
			self._handle(msg)
			logging.info("{0} Got returned data from {1}: {2}".format(datetime.now(), data.getName().toUri(), msg))

		except UnicodeDecodeError:
				logging.error("{0} Failed to parse data. Password on server?".format(datetime.now()))

		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)
		

	# Callback for when interest times out
	def _onTimeout(self, interest):
		# logging.info("{0} TIMEOUT: {1}".format(datetime.now(), interest.getName().toUri()))
		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)

	
	############################################################################
	# Handle Mouse Functions
	############################################################################

	# General handler
	# Returns true if message could be handled, otherwise false
	def _handle(self, msg):
		if msg.startswith(b"M") or msg.startswith(b"A"):
			self._handleMove(msg)
		elif msg.startswith(b"S"):
			self._handleScroll(msg)
		elif msg.startswith(b"C"):
			_, click, updown = msg.decode().split('_')
			self._handleClick(click, updown)
		elif msg.startswith(b"K"):
			_, keypress, updown = msg.decode().split('_')
			self._handleKeypress(keypress, updown)
		elif msg.startswith(b"T"):
			self._handleTypeMessage(msg)
		elif msg.startswith(b"BEAT"):
			pass  # Ignore, out of order heartbeat response
		else:
			logging.error("{0} Bad command received. Password on server?".format(datetime.now()))
			return False
		return True

	# Handle click commands
	def _handleClick(self, click, updown):
		if updown == "U":	# Up
			pyautogui.mouseUp(button=click)
		elif updown == "D":	# Down
			pyautogui.mouseDown(button=click)
		elif updown == "F":	# Full
			pyautogui.click(button=click)
		else:
			logging.error("{0} Invalid click type: {1} {2}".format(datetime.now(), click, updown))

	# Handle keypress commands
	def _handleKeypress(self, keypress, updown):
		# Decompress certain longer commands
		if keypress == "bspace":
			keypress = "backspace"

		if updown == "U":	# UP
			pyautogui.keyUp(keypress)
		elif updown == "D":	# DOWN
			pyautogui.keyDown(keypress)
		elif updown == "F":	# FULL
			pyautogui.press(keypress)
		else:
			logging.error("{0} Invalid keypress type: {1} {2}".format(datetime.now(), keypress, updown))

	# Handle custom typed message
	# Format of commands:  T<msg-to-type> (msg-to-type can be up to 10B)
	#   b"Thello"	(type "hello" on client)
	def _handleTypeMessage(self, msg):
		type_string = msg.decode()[1:]
		pyautogui.typewrite(type_string)

	# Handle movement commands
	# Format of commands:  M<x-4B><y-4B>
	#	b"A\x00\x00\x01\x90\x00\x00\x01\xf4"	(move to absolute pixel coordinate x=400, y=500)
	#	b"M\xff\xff\xff\xb5\x00\x00\x00\x19"	(move 75 left, 25 up relative to current pixel position)
	def _handleMove(self, data):
		move_type = data[:1]
		x = intFromBytes(data[1:5])
		y = intFromBytes(data[5:9])

		# Move mouse according to move_type (relative or absolute)
		if (move_type == b"M"):
			pyautogui.moveRel(x, y, self.transition_time)
		elif (move_type == b"A"):
			pyautogui.moveTo(x, y, self.transition_time)

	# Handle two-finger scroll commands
	# Format of commands:  S<x-4B><y-4B>
	#   b"S\xff\xff\xff\xb5\x00\x00\x00\x19"	(scroll 75 right, 25 up)
	def _handleScroll(self, data):
		move_type = data[:1]
		x = intFromBytes(data[1:5])
		y = intFromBytes(data[5:9])
		# Prevent bug with pyautogui library where x < 10 causes opposite horizontal scrolling behavior
		# https://github.com/asweigart/pyautogui/issues/154
		if not (-9 <= x and x <= -1):
			pyautogui.hscroll(x)
		if y:
			pyautogui.vscroll(y)


################################################################################
# Class ndnMouseClientNDNSecure
################################################################################

# Packet description
#                     1                   2                   3
# 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 
# -----------------------------------------------------------------
# |              IV               |  Seq  |  Message (PKCS5 pad)  |
# -----------------------------------------------------------------
# <~~~~~~~~~ plaintext ~~~~~~~~~~~><~~~~~~~~~ ciphertext ~~~~~~~~~>


class ndnMouseClientNDNSecure(ndnMouseClientNDN):

	# Constants
	seq_num_bytes = 4
	iv_bytes = 16
	salt_bytes = 16
	key_bytes = 16
	aes_block_size = 16
	packet_bytes = 48
	max_bad_responses = 5
	max_seq_num = 2147483647


	def __init__(self, addr, password):
		super().__init__(addr)
		self.password = password
		self.rndfile = Random.new()
		self.seq_num = 0
		self.bad_response_count = 0
		self.pending_sync = False


	def run(self):
		print("Use ctrl+c quit at anytime....")
		print("Routing /ndnmouse interests to Face udp://{0}.".format(self.server_address))
		logging.info("{0} Use ctrl+c quit at anytime....".format(datetime.now()))
		logging.info("{0} Routing /ndnmouse interests to Face udp://{1}.".format(datetime.now(), self.server_address))

		# Request password salt from producer
		self._requestSalt()

		# Wait for salt data to come back
		while not self.salt_received:
			self.face.processEvents()
			time.sleep(self.sleep_time)

		# Make interest to get movement data
		interest_move = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/update"))
		interest_move.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_move.setMustBeFresh(True)

		# Send move and click interests
		self.face.expressInterest(interest_move, self._onData, self._onTimeout)

		# Loop forever, processing data as it comes back
		# Additional interests are sent by _onData and _onTimeout callbacks
		while True:			
			self.face.processEvents()
			time.sleep(self.sleep_time)


	def shutdown(self):
		self.face.shutdown()


	############################################################################
	# Interest Callbacks and Helpers
	############################################################################

	# Callback when data is returned for a general mouse command interest
	def _onData(self, interest, data):
		data_bytes = bytes(data.getContent().buf())
		server_iv = data_bytes[:self.iv_bytes]
		encrypted = data_bytes[self.iv_bytes:]
		try:
			decrypted = self._decryptData(encrypted, server_iv)
			server_seq_num = intFromBytes(decrypted[:self.seq_num_bytes])

			# If decrypted response has a valid seq num...
			if server_seq_num > self.seq_num or self.seq_num == self.max_seq_num:
				msg = decrypted[self.seq_num_bytes:]

				# Only update seq num if we handled the command, also reset bad response count
				if self._handle(msg):
					self.seq_num = server_seq_num
					self.bad_response_count = 0

				logging.debug("{0} Got returned data from {1}: {2}".format(datetime.now(), data.getName().toUri(), msg))

			else:
				logging.error("{0} Bad sequence number received!".format(datetime.now()))
				self.bad_response_count += 1
				if self.bad_response_count > self.max_bad_responses:
					# Send special interest to update server's seq num
					self._syncWithServer()

		except (UnicodeDecodeError, ValueError):
			logging.error("{0} Failed to decrypt data. Wrong password?".format(datetime.now()))
			self.bad_response_count += 1
			if self.bad_response_count > self.max_bad_responses:
				self._syncWithServer()
			
		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)
		

	# Callback when timeout for a general mouse command interest
	def _onTimeout(self, interest):
		# logging.info("{0} TIMEOUT: {1}".format(datetime.now(), interest.getName().toUri()))
		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)


	# Send a salt request interest
	def _requestSalt(self):
		logging.info("{0} Sending salt request interest: /ndnmmouse/salt".format(datetime.now()))
		self.salt_received = False
		interest_salt = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/salt"))
		interest_salt.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_salt.setMustBeFresh(True)
		self.face.expressInterest(interest_salt, self._onSaltData, self._onSaltTimeout)


	# Callback when data is returned for getting password salt from producer
	def _onSaltData(self, interest, data):
		# Validate salt is correct length
		salt = bytes(data.getContent().buf())
		logging.info(str(datetime.now()).encode() + b" Received salt data: " + salt)
		if len(salt) == self.salt_bytes:
			# Get key from password and salt
			self.key = self._getKeyFromPassword(self.password, salt)
			self.salt_received = True
		else:
			# Otherwise try requesting salt again
			self.face.expressInterest(interest, self._onSaltData, self._onSaltTimeout)


	# Callback when timeout for getting password salt from producer
	def _onSaltTimeout(self, interest):
		# logging.info("{0} TIMEOUT: /ndnmouse/salt".format(datetime.now()))
		# Just resend interest
		self.face.expressInterest(interest, self._onSaltData, self._onSaltTimeout)
	

	# Send a special interest to update the synchronize the server's seq num with consumer
	def _setServerSeqNum(self):
		# If seq num passed INT_MAX, then reset to 0
		if self.seq_num >= self.max_seq_num:
			self.seq_num = 0

		# Make interest name
		iv = self._getNewIV()
		msg = intToBytes(self.seq_num) + b"SEQ"
		encrypted_seq_num = self._encryptData(msg, iv)
		interest_name = pyndn.name.Name("/ndnmouse/seq/")
		interest_name.append(pyndn.name.Name.Component(iv + encrypted_seq_num))

		# Make interest and send out face
		interest_update_seq = pyndn.interest.Interest(interest_name)
		interest_update_seq.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_update_seq.setMustBeFresh(True)
		logging.debug("{0} Sending set seq num interest: ".format(datetime.now()) + interest_name.toUri())
		self.face.expressInterest(interest_update_seq, self._onUpdateSeqData, self._onUpdateSeqTimeout)

	
	# Callback when data is returned for an update seq num interest
	def _onUpdateSeqData(self, interest, data):
		data_bytes = bytes(data.getContent().buf())
		server_iv = data_bytes[:self.iv_bytes]
		encrypted = data_bytes[self.iv_bytes:]
		decrypted = self._decryptData(encrypted, server_iv)
		server_seq_num = intFromBytes(decrypted[:self.seq_num_bytes])

		# If decrypted response has a valid seq num...
		if server_seq_num > self.seq_num or self.seq_num == self.max_seq_num:
			try:
				msg = decrypted[self.seq_num_bytes:]
				# Good response received, no additional update seq interests needed
				if msg.startswith(b"SEQ-ACK"):
					self.seq_num = server_seq_num
					return
			
			except UnicodeDecodeError:
				logging.error("{0} Failed to decrypt data. Wrong password?".format(datetime.now()))
				self.bad_response_count += 1
				if self.bad_response_count > self.max_bad_responses:
					self._syncWithServer()
		else:
			logging.error("{0} Bad sequence number received!".format(datetime.now()))
				
		# Resend update seq interest, because we didn't get proper response back
		self.face.expressInterest(interest, self._onUpdateSeqData, self._onUpdateSeqTimeout)	


	# Callback when timeout for an update seq num interest
	def _onUpdateSeqTimeout(self, interest):
		# logging.info("{0} TIMEOUT: /ndnmouse/seq".format(datetime.now()))
		# Resend interest to try to synchronize seq nums again
		self.face.expressInterest(interest, self._onUpdateSeqData, self._onUpdateSeqTimeout)


	# If consumer gets bad responses, sync with server by re-getting password 
	# salt and setting the server's seq num
	def _syncWithServer(self):
		# Don't try to sync if sync is already pending
		if self.pending_sync:
			return
		self.pending_sync = True

		logging.info("{0} Attempting to synchronize with server".format(datetime.now()))
		# Get password salt
		self._requestSalt()
		# Wait for salt data to return
		while not self.salt_received:
			self.face.processEvents()
			time.sleep(self.sleep_time)

		# Set server's seq num
		self._setServerSeqNum()

		# Reset bad response count and sync is complete
		self.bad_response_count = 0
		self.pending_sync = False

	# General handler
	# Returns true if message could be handled, otherwise false
	def _handle(self, msg):
		if msg.startswith(b"M") or msg.startswith(b"A"):
			self._handleMove(msg)
		elif msg.startswith(b"S"):
			self._handleScroll(msg)
		elif msg.startswith(b"C"):
			_, click, updown = msg.decode().split('_')
			self._handleClick(click, updown)
		elif msg.startswith(b"K"):
			_, keypress, updown = msg.decode().split('_')
			self._handleKeypress(keypress, updown)
		elif msg.startswith(b"T"):
			self._handleTypeMessage(msg)
		elif msg.startswith(b"BEAT"):
			pass  # Ignore, out of order heartbeat response
		else:
			logging.error("{0} Bad response data received. Wrong password?".format(datetime.now()))
			self.bad_response_count += 1
			if self.bad_response_count > self.max_bad_responses:
				self._syncWithServer()
			return False

		return True

	############################################################################
	# Encryption Helpers
	############################################################################

	# Encrypt data, message and iv are byte strings
	def _encryptData(self, message, iv):
		logging.info(str(datetime.now()).encode() + b" Data SENT: " + message)
		cipher = AES.new(self.key, AES.MODE_CBC, iv)
		message = self._PKCS5Pad(message)
		encrypted = cipher.encrypt(message)
		logging.debug(str(datetime.now()).encode() + b" Encrypting data SENT: " + encrypted)
		return encrypted

	# Decrypt data, encrypted and iv are byte strings
	def _decryptData(self, encrypted, iv):
		logging.debug(str(datetime.now()).encode() + b" Encrypted data RECEIVED: " + encrypted)
		cipher = AES.new(self.key, AES.MODE_CBC, iv)
		decrypted = self._PKCS5Unpad(cipher.decrypt(encrypted))
		logging.info(str(datetime.now()).encode() + b" Data RECEIVED: " + decrypted)
		return decrypted

	# Get a new random initialization vector (IV), return byte string
	def _getNewIV(self):		
		return self.rndfile.read(self.iv_bytes)

	# Hash password and salt (if provided) into key
	# 	password: string
	#	salt: byte string
	def _getKeyFromPassword(self, password, salt=b""):
		sha = hashlib.sha256()
		sha.update(password.encode() + salt)
		# Only take first 128 bits (16 B)
		return sha.digest()[:self.key_bytes]

	# PKCS5Padding padder, allows for longer than 16 byte pads by specifying maxPad
	def _PKCS5Pad(self, s, maxPad=aes_block_size):
		return s + (maxPad - len(s) % maxPad) * chr(maxPad - len(s) % maxPad).encode()

	# PKCS5Padding unpadder
	def _PKCS5Unpad(self, s):
		return s[0:-ord(chr(s[-1]))]


################################################################################
# User Input Functions
################################################################################

# Prompt user for server address and port, and validate them
def getServerAddress(default_addr):
	last_ip_addr = "ndnMouse-temp.pkl"

	# Try to get pickle of last IP address
	try:
		with open(last_ip_addr, 'rb') as fp:
			last_addr = pickle.load(fp)
	except IOError:
		last_addr = default_addr

	addr = pyautogui.prompt(text="Enter server IP address", title="Server Address", default=last_addr)

	# Validate address
	try:
		ipaddress.ip_address(addr)
	except ValueError:
		pyautogui.alert(text="Address \"{0}\" is not valid!".format(addr), title="Invalid Address", button='Exit')
		sys.exit(1)

	# Save the last used IP address to pickle file
	with open(last_ip_addr, 'wb') as fp:
		pickle.dump(addr, fp)

	return addr


# Prompt user for password, and validate it
def getPassword():
	password = pyautogui.password(text="Enter the server's password (optional)", title="Password", mask='*')
	# if not password:
	# 	pyautogui.alert(text="Password should not be empty!", title="Invalid Password", button='Exit')
	# 	sys.exit(1)

	return password


################################################################################
# NFD Functions
################################################################################

# Checks if NFD is running
def NFDIsRunning():
	process = subprocess.Popen(["nfd-status"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = process.communicate()
	if err.startswith(b"error"):
		return False
	else:
		return True


# Setup NFD's route to the phone server's NFD
def setupNFD(addr):
	process = subprocess.Popen(["nfdc", "register", "/ndnmouse", "udp://{0}".format(addr)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
	out, err = process.communicate()
	if out.startswith(b"Successful"):
		return True
	else:
		return False


################################################################################
# Helper Functions
################################################################################

# Takes signed integer and tranforms to byte string (truncating if necessary)
def intToBytes(x):
	try:
		return x.to_bytes(4, 'big', signed=True)
	except OverflowError:
		x %= 2147483648
		return x.to_bytes(4, 'big', signed=True)

# Takes byte string and transforms to signed integer
def intFromBytes(xbytes):
	return int.from_bytes(xbytes, 'big', signed=True)


# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])