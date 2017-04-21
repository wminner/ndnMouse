#!/usr/bin/env python3

import sys
import socket, ipaddress
import pyautogui

def main(argv):
	
	default_address = '149.142.48.182'
	default_port = 10888

	# Prompt user for server address
	server_address = getServerAddress(default_address)
	#server_port = getSeverPort(default_port)	# Just leaving at default for now...

	# Prompt user for password
	password = getPassword()

	# Create server and run it
	server = ndnMouseServerUDP(server_address, default_port, password)
	
	try:
		server.run()
	except KeyboardInterrupt:
		print("\nExiting...")
	finally:
		server.shutdown()

################################################################################
# Class ndnMouseServerUDP
################################################################################

class ndnMouseServerUDP():
	
	# pyautogui variables
	transition_time = 0
	screen_size = pyautogui.size()
	pyautogui.FAILSAFE = False
	pyautogui.PAUSE = 0

	# Socket variables
	bind_address = ('', 10888)
	
	def __init__(self, addr, port, password):
		self.server_address = (addr, port)
		self.password = password


	# Send opening messge to refresh the connection (keep alive)
	def _refreshConnection(self):
		got_timeout = True
		while got_timeout:
			message = "GET {0}x{1}\n".format(*self.screen_size).encode()
			print("Sending message: {0}".format(message))
			try:
				self.sock.sendto(message, self.server_address)
				data, server = self.sock.recvfrom(64)
				if data.decode().startswith("OK"):
					got_timeout = False
					print("Connected to server {0}:{1}.".format(*server))

			except socket.timeout:
				got_timeout = True


	# Run the server
	def run(self):
		self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
		self.sock.bind(self.bind_address)
		self.sock.settimeout(1.0)

		print("Use ctrl+c quit at anytime....")
		print("Listening to {0}, port {1}.".format(*self.server_address))
				
		# Receive and process mouse updates forever
		while True:
			try:
				data, server = self.sock.recvfrom(64)
			except socket.timeout:
				self._refreshConnection()
				continue			

			clean_data = data.decode().rstrip()
			
			# Handle different commands
			if clean_data.startswith("REL") or clean_data.startswith("ABS"):
				self._handleMove(clean_data, self.transition_time)
			elif clean_data.startswith("CLICK"):
				_, click, updown = clean_data.split(' ')
				self._handleClick(click, updown)
			else:	# Got acknowledgement message from server, do nothing
				continue

			print("Received from server {0}:{1}: {2}".format(server[0], server[1], data))
		

	# Shutdown the server
	def shutdown(self):
		message = b"STOP\n"
		# print("Sending message: {0}".format(message))
		self.sock.sendto(message, self.server_address)
		self.sock.close()

	############################################################################
	# Handle Mouse Functions
	############################################################################

	# Handle click commands
	def _handleClick(self, click, updown):
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
# User Input Functions
################################################################################

# Prompt user for server address and port, and validate
def getServerAddress(default_addr):
	addr = pyautogui.prompt(text="Enter server IP address", title="Server Address", default=default_addr)
	
	# Validate address
	try:
		ipaddress.ip_address(addr)
	except ValueError:
		pyautogui.alert(text="Address \"{0}\" is not valid!".format(addr), title="Invalid Address", button='Exit')
		sys.exit(1)

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
	if not password:
		pyautogui.alert(text="Password should not be empty!", title="Invalid Password", button='Exit')
		sys.exit(1)

	return password


# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])