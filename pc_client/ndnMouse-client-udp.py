#!/usr/bin/env python3

import sys, time
import socket, ipaddress
import pyautogui

import logging
import pickle

from Crypto import Random
from Crypto.Cipher import AES
import hashlib

def main(argv):
	LOG_FILENAME = "log_udp.txt"
	logging.basicConfig(filename=LOG_FILENAME, level=logging.DEBUG)
	
	default_address = '149.142.48.182'
	default_port = 10888

	# Prompt user for server address
	server_address = getServerAddress(default_address)
	#server_port = getSeverPort(default_port)	# Just leaving at default for now...

	# Prompt user for password
	password = getPassword()

	# Create server and run it
	if not password:
		server = ndnMouseClientUDP(server_address, default_port)
	else:
		server = ndnMouseClientUDPSecure(server_address, default_port, password)
	
	try:
		server.run()
	except KeyboardInterrupt:
		print("\nExiting...")
		logging.debug("\nExiting...")
	finally:
		server.shutdown()

################################################################################
# Class ndnMouseClientUDP
################################################################################

class ndnMouseClientUDP():
	
	# pyautogui variables
	transition_time = 0
	pyautogui.FAILSAFE = False
	pyautogui.PAUSE = 0

	packet_bytes = 32
	

	def __init__(self, addr, port):
		self.server_address = (addr, port)
		self.bind_address = ('', port)


	# Establish connection with server
	def _openConnection(self):		
		got_timeout = True
		while got_timeout:
			message = b"OPEN"
			logging.debug(b"Sending message: " + message)
			try:
				self.sock.sendto(message, self.server_address)
				data, server = self.sock.recvfrom(self.packet_bytes)
				if data.decode().startswith("OPEN-ACK"):
					got_timeout = False
					logging.debug("Connected to server {0}:{1}.".format(*server))
				else:
					time.sleep(1)

			except socket.timeout:
				continue


	# Send messge to refresh the connection (heartbeat)
	def _refreshConnection(self):
		got_timeout = True
		while got_timeout:
			message = b"HEART"
			logging.debug("Sending message: {0}".format(message))
			try:
				self.sock.sendto(message, self.server_address)
				data, server = self.sock.recvfrom(self.packet_bytes)
				if data.decode().startswith("BEAT"):
					got_timeout = False
					logging.debug("Connected to server {0}:{1}.".format(*server))
				else:
					time.sleep(1)

			except socket.timeout:
				continue


	# Run the server
	def run(self):
		self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
		self.sock.bind(self.bind_address)
		self.sock.settimeout(1.0)

		print("Use ctrl+c quit at anytime....")
		print("Listening to {0}, port {1}.".format(*self.server_address))
		logging.debug("Use ctrl+c quit at anytime....")
		logging.debug("Listening to {0}, port {1}.".format(*self.server_address))

		self._openConnection()
				
		# Receive and process mouse updates forever
		while True:
			try:
				data, server = self.sock.recvfrom(self.packet_bytes)
			except socket.timeout:
				self._refreshConnection()
				continue

			msg = data.decode().rstrip()
			
			# Handle different commands
			if msg.startswith("REL") or msg.startswith("ABS"):
				self._handleMove(msg, self.transition_time)
			elif msg.startswith("CLICK"):
				_, click, updown = msg.split('_')
				self._handleClick(click, updown)
			else:	# Got acknowledgement message from server, do nothing
				continue

			logging.debug("Received from server {0}:{1}: {2}".format(server[0], server[1], data))


	# Shutdown the server
	def shutdown(self):
		message = b"CLOSE"
		logging.debug("Sending message: {0}".format(message))
		self.sock.sendto(message, self.server_address)
		self.sock.close()


	############################################################################
	# Handle Mouse Functions
	############################################################################

	# Handle click commands
	def _handleClick(self, click, updown):
		if updown == "U":  	# UP
			pyautogui.mouseUp(button=click)
		elif updown == "D":	# DOWN
			pyautogui.mouseDown(button=click)
		elif updown == "F":	# FULL
			pyautogui.click(button=click)
		else:
			logging.debug("Invalid click type: {0} {1}".format(click, updown))


	# Handle movement commands
	# Format of commands:
	#	"ABS 400,500"	(move to absolute pixel coordinate x=400, y=500)
	#	"REL -75,25"	(move 75 left, 25 up relative to current pixel position)
	def _handleMove(self, data, transition_time):
		move_type = data[:3]
		position = data[4:]
		x, y = [int(i) for i in position.split(',')]

		# Move mouse according to move_type (relative or absolute)
		if (move_type == "REL"):
			pyautogui.moveRel(x, y, transition_time)
		elif (move_type == "ABS"):
			pyautogui.moveTo(x, y, transition_time)


################################################################################
# Class ndnMouseClientUDPSecure
################################################################################

# Packet description
#                     1                   2                   3
# 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
# -----------------------------------------------------------------
# |              IV               |  Seq  |        Message        |
# -----------------------------------------------------------------
# <~~~~~~~~~ plaintext ~~~~~~~~~~~><~~~~~~~~ ciphertext ~~~~~~~~~~>

class ndnMouseClientUDPSecure(ndnMouseClientUDP):
	
	rndfile = None
	seq_num_bytes = 4
	iv_bytes = 16
	key_bytes = 16
	aes_block_size = 16


	def __init__(self, addr, port, password):
		self.server_address = (addr, port)
		self.bind_address = ('', port)
		self.key = self.getKeyFromPassword(password)


	# Establish connection with server
	def _openConnection(self):
		got_timeout = True
		while got_timeout:
			self.seq_num = 0
			iv = self.getNewIV()

			# Create message from IV, seq num, and protocol msg
			message = intToBytes(self.seq_num) + b"OPEN"
			logging.debug(b"Sending message: " + iv + message)
			encrypted_message = self.encryptData(message, iv)
			try:
				# Send and receive data
				self.sock.sendto(encrypted_message, self.server_address)
				data, server = self.sock.recvfrom(self.packet_bytes)

				# Extract cleartext IV and ciphertext response, then decrypt it
				server_iv = data[:self.iv_bytes]
				encrypted = data[self.iv_bytes:]
				decrypted = self.decryptData(encrypted, server_iv)

				# If decrypted response is what we expect...
				if decrypted.startswith(intToBytes(self.seq_num+1) + b"OPEN-ACK"):
					# Incrememt the seq num (for this case it will always be 1)
					self.seq_num = 1
					# Break out of the loop
					got_timeout = False
					logging.debug("Connected to server {0}:{1}.".format(*server))
				else:
					# If wrong response, then sleep and try again
					time.sleep(1)

			except socket.timeout:
				# No response, try again
				continue


	# Send messge to refresh the connection (heartbeat)
	def _refreshConnection(self):
		got_timeout = True
		while got_timeout:
			# Always increment seq num before sending any message (except opener msg)
			self.seq_num += 1
			iv = self.getNewIV()
			
			# Create message from IV, seq num, and protocol msg
			message = intToBytes(self.seq_num) + b"HEART"
			logging.debug(b"Sending message: " + iv + message)
			encrypted_message = self.encryptData(message, iv)
			try:
				# Send and receive data
				self.sock.sendto(encrypted_message, self.server_address)
				data, server = self.sock.recvfrom(self.packet_bytes)

				# Extract cleartext IV and ciphertext response, then decrypt it
				server_iv = data[:self.iv_bytes]
				encrypted = data[self.iv_bytes:]
				decrypted = self.decryptData(encrypted, server_iv)

				server_seq_num = intFromBytes(decrypted[:4])
				# If decrypted response has a valid seq num, and has the correct response...
				if server_seq_num > self.seq_num and decrypted[4:].startswith(b"BEAT"):
					# Update our seq num to synchronize with server
					self.seq_num = server_seq_num
					# Break out of the loop
					got_timeout = False
					logging.debug("Connected to server {0}:{1}.".format(*server))
				else:
					# If wrong response, then sleep and try again
					time.sleep(1)

			except socket.timeout:
				# No response, try again
				continue


	# Run the server
	def run(self):
		self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
		self.sock.bind(self.bind_address)
		self.sock.settimeout(1.0)

		print("Use ctrl+c quit at anytime....")
		print("Listening to {0}, port {1}.".format(*self.server_address))
		logging.debug("Use ctrl+c quit at anytime....")
		logging.debug("Listening to {0}, port {1}.".format(*self.server_address))

		self._openConnection()
				
		# Receive and process mouse updates forever
		while True:
			try:
				data, server = self.sock.recvfrom(self.packet_bytes)
			except socket.timeout:
				self._refreshConnection()
				continue

			# Extract cleartext IV and ciphertext message, then decrypt it
			server_iv = data[:self.iv_bytes]
			encrypted = data[self.iv_bytes:]
			decrypted = self.decryptData(encrypted, iv)

			server_seq_num = intFromBytes(decrypted[:4])
			# If decrypted message has a valid seq num...
			if server_seq_num > self.server_seq_num:
				msg = decrypted[4:].decode().rstrip()
			
			# Handle different commands
			if msg.startswith("REL") or msg.startswith("ABS"):
				self._handleMove(msg, self.transition_time)
			elif msg.startswith("CLICK"):
				_, click, updown = msg.split('_')
				self._handleClick(click, updown)
			else:	# Got acknowledgement message from server, do nothing
				continue

			logging.debug("Received from server {0}:{1}: {2}".format(server[0], server[1], data))


	# Shutdown the server
	def shutdown(self):
		self.seq_num += 1
		iv = self.getNewIV()

		message = intToBytes(self.seq_num) + b"CLOSE"
		logging.debug("Sending message: {0}".format(message))
		encrypted_message = self.encryptData(message, iv)

		self.sock.sendto(encrypted_message, self.server_address)
		self.sock.close()

	# Encrypt data
	def encryptData(self, message, iv):
		message = self.PKCS5Pad(message)
		logging.debug(b"Encrypting data BEFORE: " + message)
		cipher = AES.new(self.key, AES.MODE_CBC, iv)
		encrypted = cipher.encrypt(message)
		logging.debug(b"Encrypting data AFTER: " + encrypted)
		return encrypted

	# Decrypt data
	def decryptData(self, encrypted, iv):
		logging.debug(b"Decrypting data BEFORE: " + encrypted)
		cipher = AES.new(self.key, AES.MODE_CBC, iv)
		decrypted = self.PKCS5Unpad(cipher.decrypt(encrypted))
		logging.debug(b"Decrypting data AFTER: " + decrypted)
		return decrypted

	# Get a new random initialization vector (IV)
	def getNewIV(self):
		if not self.rndfile:
			self.rndfile = Random.new()
		return self.rndfile.read(self.iv_bytes)

	# Hash password into key
	def getKeyFromPassword(self, password):
		sha = hashlib.sha256()
		sha.update(password.encode())
		logging.debug(b"Password \"" + password.encode() + b"\" becomes " + sha.digest()[:self.key_bytes])
		# Only take first 128 bits (16 B)
		return sha.digest()[:self.key_bytes]

	# PKCS5Padding helpers
	def PKCS5Pad(self, s):
		return s + (self.aes_block_size - len(s) % self.aes_block_size) * chr(self.aes_block_size - len(s) % self.aes_block_size).encode()

	def PKCS5Unpad(self, s):
		return s[0:-ord(chr(s[-1]))]


################################################################################
# User Input Functions
################################################################################

# Prompt user for server address and port, and validate
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


# Prompt user for server port, and validate
def getSeverPort(default_port):
	port_string = pyautogui.prompt(text="Enter server port number", title="Server Port", default=default_port)

	# Validate port
	try:
		port = int(port_string)
		if port < 1 or port > 65535:
			raise ValueError
	except ValueError:
		pyautogui.alert(text="Port \"{0}\" is not valid! Please enter a port between 1-65535.".format(port_string), title="Invalid Port", button='Exit')
		sys.exit(1)

	return port


# Prompt user for password, and validate it
def getPassword():
	password = pyautogui.password(text="Enter the server's password", title="Password", mask='*')
	# if not password:
	# 	pyautogui.alert(text="Password should not be empty!", title="Invalid Password", button='Exit')
	# 	sys.exit(1)

	return password

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