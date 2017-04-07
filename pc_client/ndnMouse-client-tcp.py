#!/usr/bin/env python3

import sys
import socket
import pyautogui

def main(argv):
	sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	server_address = ('149.142.48.234', 10888)

	print("Connecting to {0}, port {1}.".format(*server_address))
	sock.connect(server_address)

	try:
		message = b"GET /position \n"
		print("Sending message: {0}".format(message))
		sock.sendall(message)

		# Look for a response
		while True:
			data = sock.recv(1024)
			if not data:
				break
			print("Received from server: {0}".format(data))
			

	finally:
		print("Closing socket.")
		sock.close()
		

# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])