#!/usr/bin/env python3

import sys, time
import pyndn, ipaddress
import subprocess
import pyautogui

# import logging

transition_time = 0

def main(argv):
	# LOG_FILENAME = "log.txt"
	# logging.basicConfig(filename=LOG_FILENAME, level=logging.DEBUG)

	pyautogui.FAILSAFE = False
	pyautogui.PAUSE = 0
	screen_size = pyautogui.size()
	

	# Create face to work with NFD
	face = pyndn.face.Face()
	
	# Prompt user for server address and port
	default_server_address = "149.142.48.182"
	server_address = getServerAddress(default_server_address)

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

	print("Use ctrl+c quit at anytime....")
	print("Routing /ndnmouse interests to udp://{0}.".format(server_address))

	try:
		message = "GET {0}x{1}\n".format(*screen_size).encode()
		# print("Sending message: {0}".format(message))
		
		while True:
			# Make interest
			interest = pyndn.interest.Interest(pyndn.name.Name("/ndnmouse/move"))
			interest.setInterestLifetimeMilliseconds(1000)
			# print("interest timeout is {0}".format(interest.getInterestLifetimeMilliseconds()))
			# Send interest
			face.expressInterest(interest, onData, onTimeout)
			face.processEvents()
			time.sleep(0.1)

	except KeyboardInterrupt:
		print("\nExiting....")

	finally:
		face.shutdown()
		# message = b"STOP\n"
		# print("Sending message: {0}".format(message))
		# sock.sendto(message, server_address)


# Callback for when data is returned for an interest
def onData(interest, data):
	byte_string_data = bytes(data.getContent().buf())
	clean_data = byte_string_data.decode().rstrip()
	
	# Check and handle click
	if clean_data.startswith("CLICK "):
		_, click, updown = clean_data.split(' ')
		handleClick(click, updown)
	# Otherwise assume move command
	else:
		handleMove(clean_data)

	print("Got returned data: {0}".format(clean_data))
	

# Callback for when interest times out
def onTimeout(interest):
	print("Interest \"{0}\" timed out.".format(interest.getName().toUri()))


# Handle click commands
def handleClick(click, updown):
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
def handleMove(data):
	move_type = data[:3]
	position = data[4:]
	x, y = [int(i) for i in position.split(',')]

	# Move mouse according to move_type (relative or absolute)
	if (move_type == "REL"):
		pyautogui.moveRel(x, y, transition_time)
	elif (move_type == "ABS"):
		pyautogui.moveTo(x, y, transition_time)


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