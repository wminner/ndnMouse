#!/usr/bin/env python3

import sys
import socket
import pyautogui

def main(argv):
	pyautogui.FAILSAFE = False
	screen_size = pyautogui.size()

	sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	bind_address = ('', 10888)
	server_address = ('149.142.48.234', 10888)

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
			# Otherwise assume position and set the mouse coordinate
			else:
				move_type = clean_data[:3]
				position = clean_data[4:]
				x, y = [int(i) for i in position.split(',')]

				# Move mouse according to move_type (relative or absolute)
				if (move_type == "REL"):
					pyautogui.moveRel(x, y, 0.1)
				elif (move_type == "ABS"):
					pyautogui.moveTo(x, y, 0.1)
			
			print("Received from server {0}:{1}: {2}".format(server[0], server[1], data))

	finally:
		message = b"STOP position\n"
		print("Sending message: {0}".format(message))
		sock.sendto(message, server_address)
		
		print("Closing socket.")
		sock.close()


def handleClick(click, updown):
	if updown == "up":
		pyautogui.mouseUp(button=click)
	elif updown == "down":
		pyautogui.mouseDown(button=click)
	else:
		print("Invalid click type: {0} {1}".format(click, updown))


# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])