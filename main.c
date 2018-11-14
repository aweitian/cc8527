//debug
//https://www.arc4dia.com/blog/building-and-debugging-command-line-programs-on-android/

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <strings.h>
#include <sys/types.h>
#include <sys/un.h>  
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>
#include <sys/time.h>
#include <pthread.h>
#include <errno.h>


#define PORT 13864	/* 端口号 */
#define BACKLOG 5 	/*最大监听数*/
#include <android/log.h>
#define  LOG_TAG    "Garri"
#define  LOGI(...)  if(open_log)__android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  if(open_log)__android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

int open_log = 0;

struct my_socket_pair {
	int fd_uds;
	int fd_sock;
};


/** 
 * Create a client endpoint and connect to a server.   
 * Returns fd if all OK, <0 on error. 
 */  
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
/**
 * 
 *
 */
void new_thread_handle(void * arg)
{
	struct my_socket_pair *p = arg;
	fd_set rfds;
	struct timeval tv;
	char buff[1024];
	int len,size,retval;
	while(1)
	{
		memset(buff,0,1024);
		FD_ZERO(&rfds);
		FD_SET(p->fd_uds, &rfds);
		FD_SET(p->fd_sock, &rfds);
		tv.tv_sec = 0;
		tv.tv_usec = 0;
		retval = select((p->fd_uds > p->fd_sock ? p->fd_uds : p->fd_sock) + 1, &rfds, NULL, NULL, &tv);
		
		if (retval == -1)
		{
			LOGE("select failed.");
		}
		else if (retval)
		{
			if(FD_ISSET(p->fd_uds, &rfds))
			{
				while((len = recv(p->fd_uds,buff,1024,MSG_DONTWAIT)) >= 0)
				{
					if(len == 0)/* connection closed by client */
					{
						close(p->fd_uds);
						close(p->fd_sock);
						FD_CLR(p->fd_uds, &rfds);
						FD_CLR(p->fd_sock, &rfds);
						return;
					}
					buff[len] = 0;
					size = send(p->fd_sock, buff, len, 0);
					LOGI("uds -> socket [%d] %s",size,buff);

					//close http connection when it got the result that it wanted
					if(strstr(buff,"Urls:"))
					{
						close(p->fd_uds);
						close(p->fd_sock);
						FD_CLR(p->fd_uds, &rfds);
						FD_CLR(p->fd_sock, &rfds);
						LOGI("Close http connection.");
						return;
					}
					if(size <= 0)break;
				}
				LOGI(" === end read from uds === ");
				
			}
			else if (FD_ISSET(p->fd_sock, &rfds))
			{
				
				while((len = recv(p->fd_sock,buff,1024,MSG_DONTWAIT)) >= 0)
				{
					if(len == 0)/* connection closed by client */
					{
						close(p->fd_sock);
						FD_CLR(p->fd_sock, &rfds);
						return;
					}
					buff[len] = 0;
					size = send(p->fd_uds, buff, len, 0);
					LOGI("socket -> uds [%d] %s",size,buff);
					if(size <= 0)break;
				}
				LOGI("end read from socket");
			}

		}
	
		/*  will be true. */
		else
		{
			//LOGE("Error:%s.\n",strerror(errno));
		}
	}
}


int main(int argc, char** argv)
{
	int			sockfd, new_fd;	/*socket句柄和建立连接后的句柄*/
	struct sockaddr_in	my_addr;	/*本方地址信息结构体，下面有具体的属性赋值*/
	struct sockaddr_in	their_addr;	/*对方地址信息*/
	int			sin_size,numbytes; 
	sockfd = socket( AF_INET, SOCK_STREAM, 0 );   /* 建立socket */
	
	if(argc == 2 && strcmp(argv[1],"--garri") == 0)
	{
		open_log = 1;
	}


	if ( sockfd == -1 )
	{
		LOGE( "socket failed:%d", errno ); 
		return(-1);
	}
	my_addr.sin_family	= AF_INET;		/*该属性表示接收本机或其他机器传输*/
	my_addr.sin_port	= htons( PORT );	/*端口号*/
	my_addr.sin_addr.s_addr = htonl( INADDR_ANY );	/*IP，括号内容表示本机IP*/
	bzero( &(my_addr.sin_zero), 8 );		/*将其他属性置0*/
	if ( bind( sockfd, (struct sockaddr *) &my_addr, sizeof(struct sockaddr) ) < 0 )
	{
		/* 绑定地址结构体和socket */
		LOGE( "bind error" ); 
		return(-1);
	}
	const int on=1;

	setsockopt(sockfd,SOL_SOCKET,SO_REUSEADDR,&on,sizeof(on)); 
	listen( sockfd, BACKLOG );	/* 开启监听 ，第二个参数是最大监听数 */
	LOGI( "Listening on %d\n",PORT); 



	while ( 1 )
	{
		int fd_uds = unix_socket_conn("@abs_path");
		if (fd_uds < 0)
		{
			LOGE("connect uds failed");
			LOGI("Zzz...");
			sleep(1);
			LOGI("Zzz...");
			sleep(1);
			LOGI("Zzz...");
			sleep(1);
			continue;
		}

		sin_size = sizeof(struct sockaddr_in); 
		new_fd = accept( sockfd, (struct sockaddr *) &their_addr, (socklen_t *) &sin_size );
		/* 在这里阻塞知道接收到消息，参数分别是socket句柄，接收到的地址信息以及大小 */
		if ( new_fd == -1 )
		{
			LOGE( "receive failed" );
		} else{ //int fd_uds;

			struct my_socket_pair arg;
			arg.fd_uds = fd_uds;
			arg.fd_sock = new_fd;
			new_thread_handle(&arg);
		}
	}
	return(0);
}
