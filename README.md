# TcpForward
TcpForward - TCP forwarder to overcome IPv6 DS-Lite-Tunnel restrictions of my ISP.

## What is this about? 

 Well, my ISP offers a form of DSL, IPv6 DS-Lite-Tunnel, which makes it impossible to allow
 port forwarding and make machines from my internal network reachable from the internet.

 Basicly, no IP connection can be established from the internet to my router.
 All connections have to be established from inside my local network.

 I have a linux machine, let's call it webserver.mydomain.com, hosted by an ISP that is reachable via the net.
 I have another linux machine, let's call it internal, inside my home, not reachable via the net.

 I want to enable access to my internal machine via forwarding from my webserver.

## Setup:

 In order to do so, open both ports 22222 and 22223 on the firewall of webserver.mydomain.com.

 Then run

 'java TcpForward extern' on the webserver.mydomain.com

 and run

 'java TcpForwad webserver.mydomain.com' on the internal machine.

## Workflow:

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

## Restrictions:

 TcpForward only allows one connection at any time.

