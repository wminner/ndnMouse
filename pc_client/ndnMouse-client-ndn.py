#!/usr/bin/env python3

import sys, time
import pyndn, ipaddress
import subprocess
import pyautogui

# import logging

def main(argv):
	# LOG_FILENAME = "log_ndn.txt"
	# logging.basicConfig(filename=LOG_FILENAME, level=logging.DEBUG)
	
	# Prompt user for server address (port is always 6363 for NFD)
	default_address = "149.142.48.182"
	server_address = getServerAddress(default_address)

	# Prompt user for password
	password = getPassword()

	# Create a route from this PC's NFD to phone's NFD
	if NFDIsRunning():
		if not setupNFD(server_address):
			print("Error: could not set up NFD route!\nExiting...")
			exit(1)
	else:
		print("Error: NFD is not running!\nRun \"nfd-start\" and try again.\nExiting...")
		exit(1)

	# Create server and run it
	server = ndnMouseClientNDN(server_address, password)
	try:
		server.run()
	except KeyboardInterrupt:
		print("\nExiting...")
	finally:
		server.shutdown()


################################################################################
# Class ndnMouseClientNDN
################################################################################

class ndnMouseClientNDN():

	# pyautogui variables
	transition_time = 0
	screen_size = pyautogui.size()
	pyautogui.FAILSAFE = False
	pyautogui.PAUSE = 0

	# NDN variables
	interest_timeout = 100
	sleep_time = 0.050


	def __init__(self, addr, password):
		# Create face to work with NFD
		self.face = pyndn.face.Face()
		self.server_address = addr


	def run(self):
		print("Use ctrl+c quit at anytime....")
		print("Routing /ndnmouse interests to udp://{0}.".format(self.server_address))

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
		clean_data = byte_string_data.decode().rstrip()
		
		# Check and handle click
		if clean_data.startswith("CLICK"):
			_, click, updown = clean_data.split(' ')
			self.handleClick(click, updown)
		# Otherwise assume move command
		else:
			self.handleMove(clean_data)

		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)
		print("Got returned data from {0}: {1}".format(data.getName().toUri(), clean_data))
		

	# Callback for when interest times out
	def _onTimeout(self, interest):
		# Resend interest to get move/click data
		self.face.expressInterest(interest, self._onData, self._onTimeout)

	
	############################################################################
	# Handle Mouse Functions
	############################################################################

	# Handle click commands
	def handleClick(self, click, updown):
		if updown == "up":
			pyautogui.mouseUp(button=click)
		elif updown == "down":
			pyautogui.mouseDown(button=click)
		elif updown == "full":
			pyautogui.click(button=click)
		else:
			print("Invalid click type: {0} {1}".format(click, updown))


	# Handle movement commands
	# Format of commands:
	#	"ABS 400,500"	(move to absolute pixel coordinate x=400, y=500)
	#	"REL -75,25"	(move 75 left, 25 up relative to current pixel position)
	def handleMove(self, data):
		move_type = data[:3]
		position = data[4:]
		x, y = [int(i) for i in position.split(',')]

		# Move mouse according to move_type (relative or absolute)
		if (move_type == "REL"):
			pyautogui.moveRel(x, y, self.transition_time)
		elif (move_type == "ABS"):
			pyautogui.moveTo(x, y, self.transition_time)


################################################################################
# User Input Functions
################################################################################

# Prompt user for server address and port, and validate them
def getServerAddress(default_addr):
	addr = pyautogui.prompt(text="Enter server IP address", title="Server Address", default=default_addr)

	# Validate address
	try:
		ipaddress.ip_address(addr)
	except ValueError:
		pyautogui.alert(text="Address \"{0}\" is not valid!".format(addr), title="Invalid Address", button='Exit')
		sys.exit(1)

	return addr


# Prompt user for password, and validate it
def getPassword():
	password = pyautogui.password(text="Enter the server's password", title="Password", mask='*')
	if not password:
		pyautogui.alert(text="Password should not be empty!", title="Invalid Password", button='Exit')
		sys.exit(1)

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


# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])