#!/usr/bin/env python3

import sys, select, os
import socket, ipaddress
import pyautogui

def main(argv):
	pyautogui.FAILSAFE = False
	screen_size = pyautogui.size()
	transition_time = 0.1

	sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	bind_address = ('', 10888)
	default_server_address = ('149.142.48.234', 10888)

	# Prompt user for server address and port
	server_address = getServerAddress(*default_server_address)

	# Prompt user for password
	password = getPassword()

	print("Use ctrl+c quit at anytime....")
	print("Listening to {0}, port {1}.".format(*server_address))
	sock.bind(bind_address)

	try:
		message = "GET {0}x{1}\n".format(*screen_size).encode()
		print("Sending message: {0}".format(message))
		sock.sendto(message, server_address)

		# Look for a response
		while True:
			data, server = sock.recvfrom(64)
			clean_data = data.decode().rstrip()
			
			# Check and handle click
			if clean_data.startswith("CLICK "):
				_, click, updown = clean_data.split(' ')
				handleClick(click, updown)
			# Otherwise assume move command
			else:
				handleMove(clean_data, transition_time)
			
			print("Received from server {0}:{1}: {2}".format(server[0], server[1], data))

	finally:
		message = b"STOP\n"
		print("Sending message: {0}".format(message))
		sock.sendto(message, server_address)
		
		print("Closing socket.")
		sock.close()


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
def handleMove(data, transition_time):
	move_type = data[:3]
	position = data[4:]
	x, y = [int(i) for i in position.split(',')]

	# Move mouse according to move_type (relative or absolute)
	if (move_type == "REL"):
		pyautogui.moveRel(x, y, transition_time)
	elif (move_type == "ABS"):
		pyautogui.moveTo(x, y, transition_time)


# Prompt user for server address and port, and validate them
def getServerAddress(default_addr, default_port):
	addr = pyautogui.prompt(text="Enter server IP address", title="Server Address", default=default_addr)
	port_string = pyautogui.prompt(text="Enter server port number", title="Server Port", default=default_port)

	# Validate address
	try:
		ipaddress.ip_address(addr)
	except ValueError:
		pyautogui.alert(text="Address \"{0}\" is not valid!".format(addr), title="Invalid Address", button='Exit')
		sys.exit(1)

	# Validate port
	try:
		port = int(port_string)
		if port < 1 or port > 65535:
			raise ValueError
	except ValueError:
		pyautogui.alert(text="Port \"{0}\" is not valid! Please enter a port between 1-65535.".format(port_string), title="Invalid Port", button='Exit')
		sys.exit(1)

	return (addr, port)

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