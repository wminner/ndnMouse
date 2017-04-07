#!/usr/bin/env python3

import sys
import socket
import pyautogui

def main(argv):
	sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	bind_address = ('', 10888)
	server_address = ('149.142.48.234', 10888)

	print("Listening to {0}, port {1}.".format(*server_address))
	sock.bind(bind_address)

	try:
		message = b"GET position\n"
		print("Sending message: {0}".format(message))
		sock.sendto(message, server_address)

		# Look for a response
		while True:
			data, server = sock.recvfrom(1024)
			print("Received from server {0}:{1}: {2}".format(server[0], server[1], data))
			

	finally:
		message = b"STOP position\n"
		print("Sending message: {0}".format(message))
		sock.sendto(message, server_address)
		
		print("Closing socket.")
		sock.close()
		

# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])