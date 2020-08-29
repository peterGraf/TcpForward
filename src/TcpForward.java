/*
TcpForward - TCP forwarder to overcome IPv6 DS-Lite-Tunnel restrictions of my ISP.

Copyright (C) 2020, Peter Graf - All Rights Reserved.

    TcpForward is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    TcpForward is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with TcpForward.  If not, see <https://www.gnu.org/licenses/>.

For more information on 

Peter Graf, see www.mission-base.com/peter/
TcpForward, see www.github.com/peterGraf/TcpForward

*/

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;

public class TcpForward {

	private int internalPort = 22223;
	private int externalPort = 22222;

	private ServerSocket internalServerSocket = null;
	private ServerSocket externalServerSocket = null;
	private Socket internalSocket = null;
	private Socket externalSocket = null;

	private volatile Boolean keepForwardingBytes = false;

	public static void main(String[] args) {

		TcpForward tcpForward = new TcpForward();
		tcpForward.runTcpForward(args);
		System.exit(0);
	}

	private void runTcpForward(String[] args) {

		if (args.length != 1) {
			System.err.println("Usage: java TcpForward extern|<hostname of external host>");
			System.exit(1);
		}

		for (int i = 0; i < args.length; i++) {
			println("args[" + i + "] = '" + args[i] + "'");
		}

		if ("extern".equals(args[0])) {
			runExtern();
		} else {
			runIntern(args[0]);
		}
	}

	private void runExtern() {

		for (;;) {
			try {
				keepForwardingBytes = false;
				closeSockets();

				println("Listening on internal port " + internalPort);
				internalServerSocket = new ServerSocket(internalPort);

				internalSocket = internalServerSocket.accept();
				println("Internal connection accepted from " + internalSocket.getInetAddress());

				println("Listening on external port " + externalPort);
				externalServerSocket = new ServerSocket(externalPort);

				externalSocket = externalServerSocket.accept();
				println("External connection accepted from " + externalSocket.getInetAddress());

				startForwardingBytes();
				while (keepForwardingBytes) {
					Thread.sleep(100);
				}
			} catch (Exception e) {
				if (Thread.interrupted()) {
					println("Interrupted, bye");
					return;
				}
				println("Exception: " + e.getMessage());
			}
			sleepOneSecond();
		}
	}

	private void runIntern(String hostName) {

		byte[] bytes = new byte[1024];
		for (;;) {
			try {
				keepForwardingBytes = false;
				closeSockets();

				println("Connecting to internal " + hostName + ":" + internalPort);
				internalSocket = new Socket(hostName, internalPort);
				println("Connected to internal " + hostName + ":" + internalPort);

				println("Waiting for bytes on internal connection");
				DataInputStream internalStream = new DataInputStream(
						new BufferedInputStream(internalSocket.getInputStream()));

				int n = internalStream.read(bytes);
				if (n < 1) {
					println("Connection closed");
					sleepOneSecond();
					continue;
				}
				println("Received bytes on internal connection: " + n);

				println("Connecting to localhost:22");
				externalSocket = new Socket("localhost", 22);
				println("Connected to localhost:22");

				DataOutputStream externalStream = new DataOutputStream(
						new BufferedOutputStream(externalSocket.getOutputStream()));
				externalStream.write(bytes, 0, n);
				externalStream.flush();
				println("Forwarded bytes to external connection: " + n);

				startForwardingBytes();
				while (keepForwardingBytes) {
					Thread.sleep(100);
				}
			} catch (Exception e) {
				if (Thread.interrupted()) {
					println("Interrupted, bye");
					return;
				}
				println("Exception: " + e.getMessage());
			}
			sleepOneSecond();
		}
	}

	private void sleepOneSecond() {
		try {
			Thread.sleep(1000);
		} catch (Exception e) {
			if (Thread.interrupted()) {
				println("Interrupted, bye");
				return;
			}
			println("Exception: " + e.getMessage());
			keepForwardingBytes = false;
		}
	}

	private void closeSockets() throws IOException {
		if (externalServerSocket != null) {
			externalServerSocket.close();
			externalServerSocket = null;
		}
		if (internalServerSocket != null) {
			internalServerSocket.close();
			internalServerSocket = null;
		}
		if (externalSocket != null) {
			externalSocket.close();
			externalSocket = null;
		}
		if (internalSocket != null) {
			internalSocket.close();
			internalSocket = null;
		}
	}

	private synchronized void println(String text) {
		System.out.println(text);
	}
	
	private void startForwardingBytes() {
		keepForwardingBytes = true;
		ForwardBytes forwardToExternal = new ForwardBytes(internalSocket, externalSocket,
				"Forwarded bytes to external connection: ");
		forwardToExternal.start();

		ForwardBytes forwardToInternal = new ForwardBytes(externalSocket, internalSocket,
				"Forwarded bytes to internal connection: ");
		forwardToInternal.start();
	}

	private class ForwardBytes extends Thread {

		private Socket from;
		private Socket to;
		private String text;

		public ForwardBytes(Socket pFrom, Socket pTo, String pText) {
			from = pFrom;
			to = pTo;
			text = pText;
		}

		public void run() {

			try {
				from.setSoTimeout(100);

				byte[] bytes = new byte[32 * 1024];
				DataInputStream inputStream = new DataInputStream(new BufferedInputStream(from.getInputStream()));
				DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(to.getOutputStream()));

				while (keepForwardingBytes) {
					try {
						int n = inputStream.read(bytes);
						if (n < 1) {
							println("Connection closed");
							break;
						}
						outputStream.write(bytes, 0, n);
						outputStream.flush();
						println(text + n);
					} catch (SocketTimeoutException ex) {
						if (Thread.interrupted()) {
							println("Interrupted, bye");
							break;
						}
					}
				}
			} catch (Exception e) {
				if (Thread.interrupted()) {
					println("Interrupted, bye");
				} else {
					println("Exception: " + e.getMessage());
				}
			}
			keepForwardingBytes = false;
		}
	}
}
