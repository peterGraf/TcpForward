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

**

What is this about? 

 Well, my ISP offers a form of DSL, IPv6 DS-Lite-Tunnel, which makes it impossible to allow
 port forwarding and make machines from my internal network reachable from the internet.

 Basicly, no IP connection can be established from the internet to my router.
 All connections have to be established from inside my local network.

 I have a linux machine, let's call it webserver.mydomain.com, hosted by an ISP that is reachable via the net.
 I have another linux machine, let's call it internal, inside my home, not reachable via the net.

 I want to enable access to my internal machine via forwarding from my webserver.

 In order to do so, open both ports 22222 and 22223 on the firewall of webserver.mydomain.com.

 Then run

 'java TcpForward extern' on the webserver.mydomain.com

 and run

 'java TcpForwad webserver.mydomain.com' on the internal machine.

Workflow:

- TcpForward on the webserver listens on port 22223.

- TcpForward on the internal connects to webserver:22223,
  this connection is established from inside my local network.
  
- TcpForward on the webserver listens on port 22222.

- TcpForward on the internal waits for bytes from the connection. 

- The external user starts an ssh session to the webserver:

   ssh webserver.mydomain.com -p 22222

- TcpForward on the webserver receives bytes on port 22222.

- TcpForward on the webserver forwards the bytes to TcpForward on the internal.

- TcpForward on the internal receives bytes.

- TcpForward on the internal connects to localhost port 22, the local ssh server.

- TcpForward on the internal sends the received bytes to the ssh server.

- Both TcpForwards now forward all traffic between the external user and the internal ssh server.

- If anything goes wrong, both TcpForwards revert to step one and two.

Restrictions:

 TcpForward only allows one connection at any time.
*/

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;

public class TcpForward {

	private int internalPort = 22223;
	private int externalPort = 22222;

	private ServerSocket internalServerSocket = null;
	private ServerSocket externalServerSocket = null;
	private Socket internalSocket = null;
	private Socket externalSocket = null;

	public void run(String[] args) {

		if (args.length != 1) {
			System.err.println("Usage: java TcpForward extern|<hostname of external host>");
			System.exit(1);
		}

		for (int i = 0; i < args.length; i++) {
			Println("args[" + i + "] = '" + args[i] + "'");
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

				Println("Listening on internal port " + internalPort);

				internalServerSocket = new ServerSocket(internalPort);
				internalSocket = internalServerSocket.accept();

				Println("Internal connection accepted");
				Println("Listening on external port " + externalPort);

				externalServerSocket = new ServerSocket(externalPort);
				externalSocket = externalServerSocket.accept();

				Println("External connection accepted");

				DoRun = true;
				CopyBytes copyToExternal = new CopyBytes(internalSocket, externalSocket, "Internal socket read bytes ");
				copyToExternal.start();

				CopyBytes copyToInternal = new CopyBytes(externalSocket, internalSocket, "External socket read bytes ");
				copyToInternal.start();

				while (DoRun) {
					Thread.sleep(100);
				}
			} catch (Exception e) {
				if (Thread.interrupted()) {
					Println("Interrupted, bye");
					return;
				}
				Println("Exception: " + e.getMessage());
			}

			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				if (Thread.interrupted()) {
					Println("Interrupted, bye");
					return;
				}
				Println("Exception: " + e.getMessage());
			}
		}
	}

	private void runIntern(String hostName) {

		for (;;) {
			try {
				DoRun = false;
				if (externalSocket != null) {
					externalSocket.close();
					externalSocket = null;
				}
				if (internalSocket != null) {
					internalSocket.close();
					internalSocket = null;
				}

				Println("Connecting to external " + hostName + ":" + internalPort);

				externalSocket = new Socket(hostName, internalPort);

				Println("Connected to external " + hostName + ":" + internalPort);

				Println("Reading on external connection");
				DataInputStream externalInput = new DataInputStream(
						new BufferedInputStream(externalSocket.getInputStream()));
				
				byte[] bytes = new byte[1024];
				int n = externalInput.read(bytes);
				if (n < 1) {
					try {
						Thread.sleep(1000);
					} catch (Exception e) {
						if (Thread.interrupted()) {
							Println("Interrupted, bye");
							return;
						}
						Println("Exception: " + e.getMessage());
					}
					continue;
				}
				Println("External socket read bytes " + n);

				Println("Connecting to internal localhost:22");
				internalSocket = new Socket("localhost", 22);
				Println("Connected to internal localhost:22");

				DataOutputStream internalOutput = new DataOutputStream(new BufferedOutputStream(internalSocket.getOutputStream()));
				internalOutput.write(bytes, 0, n);
				internalOutput.flush();

				DoRun = true;
				CopyBytes copyToExternal = new CopyBytes(internalSocket, externalSocket, "Internal socket read bytes ");
				copyToExternal.start();

				CopyBytes copyToInternal = new CopyBytes(externalSocket, internalSocket, "External socket read bytes ");
				copyToInternal.start();

				while (DoRun) {
					Thread.sleep(100);
				}
			} catch (Exception e) {
				if (Thread.interrupted()) {
					Println("Interrupted, bye");
					return;
				}
				Println("Exception: " + e.getMessage());
			}

			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				if (Thread.interrupted()) {
					Println("Interrupted, bye");
					return;
				}
				Println("Exception: " + e.getMessage());
			}
		}
	}

	public static void main(String[] args) {

		TcpForward tcpForward = new TcpForward();
		tcpForward.run(args);
		System.exit(0);
	}

	private synchronized void Println(String text) {
		System.out.println(text);
	}

	private volatile Boolean DoRun = false;

	private class CopyBytes extends Thread {

		private Socket From;
		private Socket To;
		private String Text;

		public CopyBytes(Socket from, Socket to, String text) {
			From = from;
			To = to;
			Text = text;
		}

		public void run() {

			try {
				From.setSoTimeout(100);

				DataInputStream input = new DataInputStream(new BufferedInputStream(From.getInputStream()));
				DataOutputStream output = new DataOutputStream(new BufferedOutputStream(To.getOutputStream()));
				byte[] bytes = new byte[32 * 1024];

				while (DoRun) {
					try {
						int n = input.read(bytes);
						if (n < 1) {
							DoRun = false;
							break;
						}
						Println(Text + n);
						output.write(bytes, 0, n);
						output.flush();
					} catch (SocketTimeoutException ex) {
						if (Thread.interrupted()) {
							Println("Interrupted, bye");
							DoRun = false;
						}
					}
				}
			} catch (Exception e) {
				if (Thread.interrupted()) {
					Println("Interrupted, bye");
					DoRun = false;
				}
				Println("Exception: " + e.getMessage());
			}
		}
	}
}
