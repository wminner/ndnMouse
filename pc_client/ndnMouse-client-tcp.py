#!/usr/bin/env python3

import sys
import socket
import pyautogui

def main(argv):
	pyautogui.FAILSAFE = False
	pyautogui.PAUSE = 0
	screen_size = pyautogui.size()
	transition_time = 0

	sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	server_address = ('149.142.48.182', 10888)

	print("Use ctrl+c quit at anytime....")
	print("Connecting to {0}, port {1}.".format(*server_address))
	sock.connect(server_address)

	try:
		message = "GET {0}x{1}\n".format(*screen_size).encode()
		print("Sending message: {0}".format(message))
		sock.sendall(message)

		# Look for a response
		while True:
			data = sock.recv(64)
			clean_data = data.decode().rstrip()

			# Check and handle click
			if clean_data.startswith("CLICK "):
				_, click, updown = clean_data.split(' ')
				handleClick(click, updown)
			# Otherwise assume move command
			else:
				handleMove(clean_data)
			
			print("Received from server {0}:{1}: {2}".format(server[0], server[1], data))
			
	finally:
		message = b"STOP\n"
		print("Sending message: {0}".format(message))
		sock.sendall(message)
		
		print("Closing socket.")
		sock.close()


def handleClick(click, updown):
	if updown == "up":
		pyautogui.mouseUp(button=click)
	elif updown == "down":
		pyautogui.mouseDown(button=click)
	else:
		print("Invalid click type: {0} {1}".format(click, updown))


def handleMove(data):
	move_type = data[:3]
	position = data[4:]
	x, y = [int(i) for i in position.split(',')]
	
	# Move mouse according to move_type (relative or absolute)
	if (move_type == "REL"):
		pyautogui.moveRel(x, y, transition_time)
	elif (move_type == "ABS"):
		pyautogui.moveTo(x, y, transition_time)


# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])