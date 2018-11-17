#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <netdb.h>
#include <sys/un.h>  
#include <android/log.h>
#define  LOG_TAG    "Garri"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)



int unix_socket_conn(const char *servername)  
{   
	int fd;   
	if ((fd = socket(AF_UNIX, SOCK_STREAM, 0)) < 0)
	{  
		/* create a UNIX domain stream socket */		
		return(-1);  
	}  
	int len, rval;  
	struct sockaddr_un un;            
	memset(&un, 0, sizeof(un));
	/* fill socket address structure with our address */  
	un.sun_family = AF_UNIX;   

	/* fill socket address structure with server's address */  
	memset(&un, 0, sizeof(un));   
	un.sun_family = AF_UNIX;   
	strcpy(un.sun_path, servername);   
	len = offsetof(struct sockaddr_un, sun_path) + strlen(servername);  
	un.sun_path[0] = 0; 
	if (connect(fd, (struct sockaddr *)&un, len) < 0)   
	{  
		return -4;   
	}   
	else  
	{  
		return fd;  
	}  
} 

/*struct addrinfo 
{
	int              ai_flags;
	int              ai_family;
	int              ai_socktype;
	int              ai_protocol;
	size_t           ai_addrlen;
	struct sockaddr *ai_addr;
	char            *ai_canonname;
	struct addrinfo *ai_next;
}; */

static int create_and_bind(char* port)
{
	struct addrinfo hints;
	struct addrinfo*result,*rp;
	int s,sfd;

	memset(&hints,0,sizeof(struct addrinfo));
	hints.ai_family= AF_UNSPEC;/* Return IPv4 and IPv6 */
	hints.ai_socktype= SOCK_STREAM;/* TCP socket */
	hints.ai_flags= AI_PASSIVE;/* All interfaces */

	s = getaddrinfo(NULL, port,&hints,&result); //more info about getaddrinfo() please see:man getaddrinfo!
	if(s != 0)
	{
		fprintf(stderr,"getaddrinfo: %s\n",gai_strerror(s));
		return -1;
	}
	for(rp= result;rp!= NULL;rp=rp->ai_next)
	{
		sfd = socket(rp->ai_family,rp->ai_socktype,rp->ai_protocol);
		if(sfd==-1)
			continue;
		s =bind(sfd,rp->ai_addr,rp->ai_addrlen);
		if(s ==0)
		{
			/* We managed to bind successfully! */
			break;
		}
		close(sfd);
	}

	if(rp== NULL)
	{
		fprintf(stderr,"Could not bind\n");
		return-1;
	}
	freeaddrinfo(result);
	return sfd;
}

static int make_socket_non_blocking(int sfd)
{
	int flags, s;
	flags = fcntl(sfd, F_GETFL,0);
	if(flags == -1)
	{
		perror("fcntl");
		return-1;
	}

	flags|= O_NONBLOCK;
	s =fcntl(sfd, F_SETFL, flags);
	if(s ==-1)
	{
		perror("fcntl");
		return-1;
	}
	return 0;
}

#define U2S 0
#define S2U 1

#define MAXEVENTS 64

	
struct my_socket_pair {
	int fd_uds;		//val
	int fd_sock;	//key
};
struct my_socket_pair hashMap_u2s[MAXEVENTS];
struct my_socket_pair hashMap_s2u[MAXEVENTS];
void sp_init()
{
	int i;
	for(i=0;i<MAXEVENTS;i++)
	{
		hashMap_u2s[i].fd_uds = -1;
		hashMap_u2s[i].fd_sock = -1;
		hashMap_s2u[i].fd_uds = -1;
		hashMap_s2u[i].fd_sock = -1;
	}	
}

int sp_find(int k,int m)
{
	int i;
	for(i=MAXEVENTS-1;i>=0;i--)
	{
		if(m == U2S)
		{
			if(hashMap_u2s[i].fd_uds ==	k)
			{
				return i;	
			}
		}
		else
		{
			if(hashMap_s2u[i].fd_sock == k)
			{
				return i;	
			}
		}
	}
	return -1;
}


int sp_insert(int k,int v,int m)
{
	int i;
	if((i=sp_find(k,m)) !=-1)
	{
		if(m == U2S)
			hashMap_u2s[i].fd_sock = v;
		else
			hashMap_s2u[i].fd_uds = v;
		return i;
	}
	if((i=sp_find(-1,m)) !=-1)
	{
		if(m == U2S)
		{
			hashMap_u2s[i].fd_uds = k;
			hashMap_u2s[i].fd_sock = v;
		}
		else
		{
			hashMap_s2u[i].fd_sock = k;
			hashMap_s2u[i].fd_uds = v;
		}
		return i;
	}
	return  -1;
}
int sp_get(int k,int m)
{
	int i;
	if((i=sp_find(k,m)) !=-1)
	{
		if(m == U2S)
		{
			return hashMap_u2s[i].fd_sock;
		}
		else
		{
			return hashMap_s2u[i].fd_uds;
		}
	}
	return -1;	
}
int sp_reset(int k,int m)
{
	int i;
	if((i=sp_find(k,m)) !=-1)
	{
		if(m == U2S)
		{
			hashMap_u2s[i].fd_uds = -1;
			hashMap_u2s[i].fd_sock = -1;
		}
		else
		{
			hashMap_s2u[i].fd_sock = -1;
			hashMap_s2u[i].fd_uds = -1;
		}
		return i;
	}
	return -1;
}


// void forward(int fd_uds,int fd_sock)
// {
// 	fd_set rfds;
// 	struct timeval tv;
// 	char buff[1024];
// 	int len,size,retval;
// 	while(1)
// 	{
// 		memset(buff,0,1024);
// 		FD_ZERO(&rfds);
// 		FD_SET(fd_uds, &rfds);
// 		FD_SET(fd_sock, &rfds);
// 		tv.tv_sec = 0;
// 		tv.tv_usec = 0;
// 		retval = select((fd_uds > fd_sock ? fd_uds : fd_sock) + 1, &rfds, NULL, NULL, &tv);
		
// 		if (retval == -1)
// 			LOGE("select failed.");
// 		else if (retval)
// 		{
// 			if(FD_ISSET(fd_uds, &rfds))
// 			{
// 				while((len = recv(fd_uds,buff,1024,MSG_WAITALL)) >= 0)
// 				{
// 					if(len == 0)/* connection closed by client */
// 					{
// 						close(fd_uds);
// 						close(fd_sock);
// 						FD_CLR(fd_uds, &rfds);
// 						FD_CLR(fd_sock, &rfds);
// 						return;
// 					}
// 					buff[len] = 0;
// 					size = send(fd_sock, buff, len, 0);
// 					LOGI("uds -> socket [%d] %s",size,buff);

// 					//close http connection when it got the result that it wanted
// 					if(strstr(buff,"\"r\": \"ws:"))
// 					{
// 						close(fd_uds);
// 						close(fd_sock);
// 						FD_CLR(fd_uds, &rfds);
// 						FD_CLR(fd_sock, &rfds);
// 						LOGI("Close http connection.");
// 						return;
// 					}
// 					if(size <= 0)break;
// 				}
// 				LOGI(" === end read from uds === ");
// 			}
// 			else if (FD_ISSET(fd_sock, &rfds))
// 			{
				
// 				while((len = recv(fd_sock,buff,1024,MSG_DONTWAIT)) >= 0)
// 				{
// 					if(len == 0)/* connection closed by client */
// 					{
// 						close(fd_sock);
// 						FD_CLR(fd_sock, &rfds);
// 						return;
// 					}
// 					buff[len] = 0;
// 					size = send(fd_uds, buff, len, 0);
// 					LOGI("socket -> uds [%d] %s",size,buff);
// 					if(size <= 0)break;
// 				}
// 				LOGI("end read from socket");
// 			}

// 		}
	
// 		/*  will be true. */
// 		else
// 		{
// 			//LOGE("Error:%s.\n",strerror(errno));
// 		}
// 	}
// }


int main(int argc,char*argv[])
{
	char buf_s2u[131072];
	char buf_u2s[131072];
	int sfd, s;
	int efd;
	int is_u;
	struct epoll_event event;
	struct epoll_event* events;
	int fd_uds,fd_sock;
	if(argc!=2)
	{
		fprintf(stderr,"Usage: %s [port]\n",argv[0]);
		exit(EXIT_FAILURE);
	}
	sp_init();
	sfd = create_and_bind(argv[1]);
	if( sfd == -1 )
		abort();

	s = make_socket_non_blocking(sfd);
	if(s ==-1)
		abort();

	s = listen(sfd, SOMAXCONN);
	if(s ==-1)
	{
		perror("listen");
		abort();
	}

	efd = epoll_create(0);
	if(efd==-1)
	{
		perror("epoll_create");
		abort();
	}

	event.data.fd=sfd;
	event.events= EPOLLIN | EPOLLET;
	s =epoll_ctl(efd, EPOLL_CTL_ADD,sfd,&event);
	if(s ==-1)
	{
		perror("epoll_ctl");
		abort();
	}

	/* Buffer where events are returned */
	events=calloc(MAXEVENTS,sizeof event);

	/* The event loop */
	while(1)
	{
		int n,i;
		n =epoll_wait(efd, events, MAXEVENTS,-1);
		for(i=0;i< n;i++)
		{
			if((events[i].events & EPOLLERR)||
					(events[i].events & EPOLLHUP)||
					(!(events[i].events & EPOLLIN)))
			{
				/* An error has occured on this fd, or the socket is not
				   ready for reading (why were we notified then?) */
				// fprintf(stderr,"epoll error\n");
				is_u = sp_find(events[i].data.fd,U2S);

				if (is_u != -1)//uds
				{
					fd_uds = events[i].data.fd;
					fd_sock = sp_get(fd_uds,U2S);
					//fprintf(stderr,"Error: fd is usd\n");
				}
				else
				{
					fd_sock = events[i].data.fd;
					fd_uds = sp_get(fd_sock,S2U);
					//fprintf(stderr,"Error: fd is sock\n");
				}
				close(fd_uds);
				close(fd_sock);
				//close(events[i].data.fd);
				continue;
			}

			else if(sfd == events[i].data.fd)
			{
				/* We have a notification on the listening socket, which
				   means one or more incoming connections. */
				printf("We have a notification on the listening socket, which means one or more incoming connections.");
				while(1)
				{
					struct sockaddr in_addr;
					socklen_t in_len;
					int infd;
					char hbuf[NI_MAXHOST],sbuf[NI_MAXSERV];

					in_len = sizeof in_addr;
					infd = accept(sfd,&in_addr,&in_len);
					if(infd==-1)
					{
						if((errno== EAGAIN)||
								(errno== EWOULDBLOCK))
						{
							/* We have processed all incoming
							   connections. */
							break;
						}
						else
						{
							perror("accept");
							break;
						}
					}

					s = getnameinfo(&in_addr,in_len,
							hbuf,sizeof hbuf,
							sbuf,sizeof sbuf,
							NI_NUMERICHOST | NI_NUMERICSERV);
					if(s ==0)
					{
						printf("Accepted connection on descriptor %d "
								"(host=%s, port=%s)\n",infd,hbuf,sbuf);
					}

					/* Make the incoming socket non-blocking and add it to the
					   list of fds to monitor. */
					s = make_socket_non_blocking(infd);
					if(s ==-1)
						abort();

					event.data.fd=infd;
					event.events= EPOLLIN | EPOLLET;
					s = epoll_ctl(efd, EPOLL_CTL_ADD,infd,&event);
					printf("add socket to EPOLL\n");
					if(s ==-1)
					{
						perror("epoll_ctl");
						abort();
					}
					fd_uds = unix_socket_conn("@uds");
					if (fd_uds < 0)
					{
						perror("connect uds failed, please start uds first.");
						abort();
					}
					s = make_socket_non_blocking(fd_uds);
					if(s ==-1)
						abort();
					printf("----------- Connect uds ok on descriptor %d ------------------\n",fd_uds);
					s = sp_insert(infd,fd_uds,S2U);
					if (s == -1)
					{
						perror("sp_insert failed.");
						abort();
					}
					s = sp_insert(fd_uds,infd,U2S);
					if (s == -1)
					{
						perror("sp_insert failed.");
						abort();
					}
					event.data.fd=fd_uds;
					event.events= EPOLLIN | EPOLLET;
					s = epoll_ctl(efd, EPOLL_CTL_ADD,fd_uds,&event);
					printf("add uds to EPOLL\n");
					if(s ==-1)
					{
						perror("epoll_ctl uds");
						abort();
					}
				}
				continue;
			}
			// else if( events[i].events&EPOLLET ) //接收到数据，读socket
			// {
			// 	printf("Closed connection on descriptor %d\n",events[i].data.fd);
			// 	fd_uds = sp_get(events[i].data.fd);
			// 	if (fd_uds !=-1)
			// 	{
			// 		//close(fd_uds);
			// 		sp_reset(events[i].data.fd);
			// 	}
			// 	close(events[i].data.fd);
			// }
			else
			{
				/* We have data on the fd waiting to be read. Read and
				   display it. We must read whatever data is available
				   completely, as we are running in edge-triggered mode
				   and won't get a notification again for the same
				   data. */
				int done =0;
				while(1)
				{
					ssize_t count;
					memset(buf_s2u,0,sizeof(buf_s2u));
					memset(buf_u2s,0,sizeof(buf_u2s));

					is_u = sp_find(events[i].data.fd,U2S);


					//这个地方要做读写同步，WIFI慢，UDS快，要等待


					if (is_u != -1)//uds
					{
						fd_uds = events[i].data.fd;
						fd_sock = sp_get(fd_uds,U2S);
						if (fd_sock ==-1)
						{
							perror("sp_get uds to sock failed");
							break;
						}
						count = read(fd_uds,buf_u2s,sizeof buf_u2s);
						if(count == -1)
						{
							/* If errno == EAGAIN, that means we have read all
							   data. So go back to the main loop. */
							if(errno!= EAGAIN)
							{
								perror("read");
								done=1;
							}
							break;
						}
						else if(count ==0)
						{
							/* End of file. The remote has closed the
							   connection. */
							perror("End of file. The remote has closed the onnection.");
							done=1;
							break;
						}
						printf("uds ->  socket：%s\n", buf_u2s);
						s = write(fd_sock,buf_u2s, count);
						if(s ==-1)
						{
							perror("write to socket");
							abort();
						}
					}
					else
					{
						
						fd_sock = events[i].data.fd;
						fd_uds = sp_get(fd_sock,S2U);
						count = read(fd_sock,buf_s2u,sizeof buf_s2u);
						if(count == -1)
						{
							/* If errno == EAGAIN, that means we have read all
							   data. So go back to the main loop. */
							if(errno!= EAGAIN)
							{
								perror("read");
								done=1;
							}
							break;
						}
						else if(count ==0)
						{
							/* End of file. The remote has closed the
							   connection. */
							perror("End of file. The remote has closed the onnection.");
							done=1;
							break;
						}
						printf("socket -> uds：%s\n", buf_s2u);
						s = write(fd_uds,buf_s2u, count);
						if(s ==-1)
						{
							perror("write to uds");
							abort();
						}
					}
				}
				if(done)
				{
					/* Closing the descriptor will make epoll remove it
					   from the set of descriptors which are monitored. */
					printf("Closed connection on descriptor");
					close(fd_uds);
					close(fd_sock);
				}
			}
		}
	}
	free(events);
	close(sfd);
	return EXIT_SUCCESS;
}