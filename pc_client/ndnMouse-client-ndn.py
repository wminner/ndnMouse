#!/usr/bin/env python3

import sys, time
import pyndn, ipaddress
import subprocess
import pyautogui

import logging
import pickle

from Crypto import Random
from Crypto.Cipher import AES
import hashlib

def main(argv):
	LOG_FILENAME = "log.txt"
	logging.basicConfig(filename=LOG_FILENAME, level=logging.INFO)
	
	# Prompt user for server address (port is always 6363 for NFD)
	default_address = "149.142.48.182"
	server_address = getServerAddress(default_address)

	# Prompt user for password
	password = getPassword()

	# Create a route from this PC's NFD to phone's NFD
	if NFDIsRunning():
		if not setupNFD(server_address):
			print("Error: could not set up NFD route!\nExiting...")
			logging.info("Error: could not set up NFD route!\nExiting...")
			exit(1)
	else:
		print("Error: NFD is not running!\nRun \"nfd-start\" and try again.\nExiting...")
		logging.info("Error: NFD is not running!\nRun \"nfd-start\" and try again.\nExiting...")
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
		logging.info("Exiting...")
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
	interest_timeout = 100
	sleep_time = 0.050


	def __init__(self, addr):
		# Create face to work with NFD
		self.face = pyndn.face.Face()
		self.server_address = addr


	def run(self):
		print("Use ctrl+c quit at anytime....")
		print("Routing /ndnmouse interests to Face udp://{0}.".format(self.server_address))
		logging.info("Use ctrl+c quit at anytime....")
		logging.info("Routing /ndnmouse interests to Face udp://{0}.".format(self.server_address))

		# Make interest to get movement data
		interest_move = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/move"))
		interest_move.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_move.setMustBeFresh(True)

		# Make interest to get click data
		interest_click = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/click"))
		interest_click.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_click.setMustBeFresh(True)

		# Send interests
		self.face.expressInterest(interest_move, self._onData, self._onTimeout)
		self.face.expressInterest(interest_click, self._onData, self._onTimeout)

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
		byte_string_data = bytes(data.getContent().buf())
		try:
			msg = byte_string_data.decode()
			
			# Handle different commands
			if msg.startswith("REL") or msg.startswith("ABS"):
				self._handleMove(msg)
			elif msg.startswith("CLK"):
				_, click, updown = msg.split('_')
				self._handleClick(click, updown)
			elif msg.startswith("KP"):
				_, keypress, updown = msg.split('_')
				self._handleKeypress(keypress, updown)

			logging.info("Got returned data from {0}: {1}".format(data.getName().toUri(), msg))

		except UnicodeDecodeError:
				logging.error("Failed to parse data. Password on server?")

		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)
		

	# Callback for when interest times out
	def _onTimeout(self, interest):
		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)

	
	############################################################################
	# Handle Mouse Functions
	############################################################################

	# Handle click commands
	def _handleClick(self, click, updown):
		if updown == "U":	# Up
			pyautogui.mouseUp(button=click)
		elif updown == "D":	# Down
			pyautogui.mouseDown(button=click)
		elif updown == "F":	# Full
			pyautogui.click(button=click)
		else:
			logging.error("Invalid click type: {0} {1}".format(click, updown))


	# Handle keypress commands
	def _handleKeypress(self, keypress, updown):
		if updown == "U":	# UP
			pyautogui.keyUp(keypress)
		elif updown == "D":	# DOWN
			pyautogui.keyDown(keypress)
		elif updown == "F":	# FULL
			pyautogui.press(keypress)
		else:
			logging.error("Invalid keypress type: {0} {1}".format(keypress, updown))


	# Handle movement commands
	# Format of commands:
	#	"ABS 400,500"	(move to absolute pixel coordinate x=400, y=500)
	#	"REL -75,25"	(move 75 left, 25 up relative to current pixel position)
	def _handleMove(self, data):
		move_type = data[:3]
		position = data[4:]
		x, y = [int(i) for i in position.split(',')]

		# Move mouse according to move_type (relative or absolute)
		if (move_type == "REL"):
			pyautogui.moveRel(x, y, self.transition_time)
		elif (move_type == "ABS"):
			pyautogui.moveTo(x, y, self.transition_time)


################################################################################
# Class ndnMouseClientNDNSecure
################################################################################

# Packet description
#                     1                   2                   3                   4
# 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8
# -------------------------------------------------------------------------------------------------
# |              IV               |  Seq  |         Message (padding via an extended PKCS5)       |
# -------------------------------------------------------------------------------------------------
# <~~~~~~~~~ plaintext ~~~~~~~~~~~><~~~~~~~~~~~~~~~~~~~~~ ciphertext ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~>


class ndnMouseClientNDNSecure(ndnMouseClientNDN):

	rndfile = None
	seq_num_bytes = 4
	iv_bytes = 16
	key_bytes = 16
	aes_block_size = 16
	packet_bytes = 48
	max_bad_seq_nums = 5
	max_seq_num = 2147483647


	def __init__(self, addr, password):
		super().__init__(addr)
		self.key = self._getKeyFromPassword(password)
		self.rndfile = Random.new()
		self.seq_num = 0
		self.bad_seq_num_count = 0
		self.pending_update_seq_interest = False


	def run(self):
		print("Use ctrl+c quit at anytime....")
		print("Routing /ndnmouse interests to Face udp://{0}.".format(self.server_address))
		logging.info("Use ctrl+c quit at anytime....")
		logging.info("Routing /ndnmouse interests to Face udp://{0}.".format(self.server_address))

		# Make interest to get movement data
		interest_move = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/move"))
		interest_move.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_move.setMustBeFresh(True)

		# Make interest to get click data
		interest_click = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/click"))
		interest_click.setInterestLifetimeMilliseconds(self.interest_timeout)
		interest_click.setMustBeFresh(True)

		# Send interests
		self.face.expressInterest(interest_move, self._onData, self._onTimeout)
		self.face.expressInterest(interest_click, self._onData, self._onTimeout)

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
			# logging.info("server seq num = {0}, client seq num = {1}".format(server_seq_num, self.seq_num))
			# If decrypted response has a valid seq num...
			if server_seq_num > self.seq_num or self.seq_num == self.max_seq_num:
				msg = decrypted[self.seq_num_bytes:].decode()
				good_cmd = True		# Goes false if could not recognize the control command

				# Handle different commands
				if msg.startswith("REL") or msg.startswith("ABS"):
					self._handleMove(msg)
				elif msg.startswith("CLK"):
					_, click, updown = msg.split('_')
					self._handleClick(click, updown)
				elif msg.startswith("KP"):
					_, keypress, updown = msg.split('_')
					self._handleKeypress(keypress, updown)
				else:
					good_cmd = false
					logging.error("Bad response data received. Wrong password?")

				# Only update seq num if we handled the command
				if good_cmd:
					self.seq_num = server_seq_num
					self.bad_seq_num_count = 0

				logging.debug("Got returned data from {0}: {1}".format(data.getName().toUri(), msg))

			else:
				logging.error("Bad sequence number received!")
				self.bad_seq_num_count += 1
				if self.bad_seq_num_count > self.max_bad_seq_nums:
					# Send special interest to update server's seq num
					self._syncSeqNum()
		except (UnicodeDecodeError, ValueError):
			logging.error("Failed to decrypt data. Wrong password?")
			
		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)
		

	# Callback when timeout for a general mouse command interest
	def _onTimeout(self, interest):
		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)


	# Send a special interest to update the synchronize the server's seq num with consumer
	def _syncSeqNum(self):
		# Don't send another update seq interest if one is already pending
		if self.pending_update_seq_interest:
			return
		self.pending_update_seq_interest = True

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
		logging.info("Sending update seq num interest: " + interest_name.toUri())
		self.face.expressInterest(interest_update_seq, self._onUpdateSeqData, self._onUpdateSeqTimeout)

	
	# Callback when data is returned for an update seq num interest
	def _onUpdateSeqData(self, interest, data):
		data_bytes = bytes(data.getContent().buf())
		server_iv = data_bytes[:self.iv_bytes]
		encrypted = data_bytes[self.iv_bytes:]
		decrypted = self._decryptData(encrypted, server_iv)

		server_seq_num = intFromBytes(decrypted[:self.seq_num_bytes])
		# logging.info("server seq num = {0}, client seq num = {1}".format(server_seq_num, self.seq_num))
		# If decrypted response has a valid seq num...
		if server_seq_num > self.seq_num or self.seq_num == self.max_seq_num:
			try:
				msg = decrypted[self.seq_num_bytes:].decode()
				# Good response received, no additional update seq interests needed
				if msg.startswith("ACK"):
					self.seq_num = server_seq_num
					self.bad_seq_num_count = 0
					self.pending_update_seq_interest = False
					return
			
			except UnicodeDecodeError:
				logging.error("Failed to decrypt data. Wrong password?")
		else:
			logging.error("Bad sequence number received!")
				
		# Resend update seq interest, because we didn't get proper response back
		self.face.expressInterest(interest, self._onUpdateSeqData, self._onUpdateSeqTimeout)	


	# Callback when timeout for an update seq num interest
	def _onUpdateSeqTimeout(self, interest):
		# Resend interest to try to synchronize seq nums again
		self.face.expressInterest(interest, self._onUpdateSeqData, self._onUpdateSeqTimeout)
	

	############################################################################
	# Encryption Helpers
	############################################################################

	# Encrypt data, message and iv are byte strings
	def _encryptData(self, message, iv):
		logging.info(b"Data SENT: " + message)
		cipher = AES.new(self.key, AES.MODE_CBC, iv)
		message = self._PKCS5Pad(message)
		encrypted = cipher.encrypt(message)
		logging.debug(b"Encrypting data SENT: " + encrypted)
		return encrypted

	# Decrypt data, encrypted and iv are byte strings
	def _decryptData(self, encrypted, iv):
		logging.debug(b"Encrypted data RECEIVED: " + encrypted)
		cipher = AES.new(self.key, AES.MODE_CBC, iv)
		decrypted = self._PKCS5Unpad(cipher.decrypt(encrypted))
		logging.info(b"Data RECEIVED: " + decrypted)
		return decrypted

	# Get a new random initialization vector (IV), return byte string
	def _getNewIV(self):		
		return self.rndfile.read(self.iv_bytes)

	# Hash password into key
	def _getKeyFromPassword(self, password):
		sha = hashlib.sha256()
		sha.update(password.encode())
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
	last_ip_addr = "temp_ndnMouse.pkl"

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
	password = pyautogui.password(text="Enter the server's password", title="Password", mask='*')
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

# Takes unsigned integer and tranforms to byte string (truncating if necessary)
def intToBytes(x):
	try:
		return x.to_bytes(4, 'big')
	except OverflowError:
		x %= 4294967296
		return x.to_bytes(4, 'big')

# Takes byte string and transforms to integer
def intFromBytes(xbytes):
	return int.from_bytes(xbytes, 'big')


# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])