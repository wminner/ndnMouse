#!/usr/bin/env python3

import sys
import socket
import pyautogui

def main(argv):
	client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	server_address = ('164.67.229.108', 10888)

	print("Connecting to {0}, port {1}.".format(server_address[0], server_address[1]))
	client_socket.connect(server_address)

	try:
		message = b"GET /position \n"
		print("Sending message: {0}".format(message))
		client_socket.sendall(message)

		# Look for a response
		while True:
			data = client_socket.recv(1024)
			if not data:
				break
			print("Received from server: {0}".format(data))
			

	finally:
		print("Closing socket.")
		client_socket.close()
		

# Strip off script name in arg list
if __name__ == "__main__":
	main(sys.argv[1:])