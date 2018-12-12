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
#include <signal.h>
#include <limits.h>
#include <sys/ipc.h>
#include <sys/sem.h>
#include <sys/stat.h>
#include <dirent.h>
// #include<sys/types.h>
// #include<sys/stat.h>
// #include<unistd.h>
// #include<fcntl.h>
// #include<stdio.h>
// #include<stdlib.h>
// #include<errno.h>
// #include<string.h>
// #include<signal.h>
#define BUF_SIZE 1024
#define LOG_TAG    "Garri"
// #define EXIT_SECRET "@RUR'URU'U'R'#13864#RUUR'U'RU'R'@"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

int g_bool_qq = 1;
int g_sock_listen;
char g_cwd[PATH_MAX];

void cleanup()
{
    if(g_sock_listen >=0)
    {
    	close(g_sock_listen);
    }
}

/**************************************************************************************************
**  函数名称:  lockfile
**  功能描述:  对文件加锁
**  输入参数:  无
**  输出参数:  无
**  返回参数:  无
**************************************************************************************************/
static int lockfile(int fd)
{
    struct flock fl;

    fl.l_type   = F_WRLCK;
    fl.l_start  = 0;
    fl.l_whence = SEEK_SET;
    fl.l_len    = 0;

    return(fcntl(fd, F_SETLK, &fl));
}



/**************************************************************************************************
**  函数名称:  proc_is_exist
**  功能描述:  判断系统中是否存在该进程
**  输入参数:  procname: 进程名
**  输出参数:  无
**  返回参数:  返回1表示系统中已经存在该进程了；返回0表示系统中不存在该进程
**  注意事项:  此处加锁完后无需对文件进行close，而是进程退出后由系统来释放；否则无法起到保护的作用
**************************************************************************************************/
int proc_is_exist()
{
    int  fd;
    char buf[16];
    char filename[PATH_MAX];

    sprintf(filename, "%s/ep.pid", g_cwd);

    fd = open(filename, O_RDWR | O_CREAT, (S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH));
    if (fd < 0) {
        printf("open file \"%s\" failed!!!\n", filename);
        return 1;
    }

    if (lockfile(fd) == -1) {                                                  /* 尝试对文件进行加锁 */
        //printf("file \"%s\" locked. proc already exit!!!\n", filename);
        close(fd);
        return 1;
    } else {
        ftruncate(fd, 0);                                                      /* 写入运行实例的pid */
        sprintf(buf, "%ld", (long)getpid());
        write(fd, buf, strlen(buf) + 1);
        return 0;
    }
}

int make_fifo_4p()
{
    char filename[PATH_MAX];
    sprintf(filename, "%s/ep.fifo", g_cwd);
    struct stat stat_p;
  	stat (filename, &stat_p);
  	if(!S_ISFIFO(stat_p.st_mode))
  	{
		mkfifo(filename, 0666); 
  	}
  	stat (filename, &stat_p);
    return S_ISFIFO(stat_p.st_mode);
}

int exit_all_process()
{
    char filename[PATH_MAX];
    sprintf(filename, "%s/ep.fifo", g_cwd);

    struct stat stat_p;
  	stat (filename, &stat_p);
  	if(!S_ISFIFO(stat_p.st_mode))
  	{
  		perror("fifo is not exists.");
		exit(0);
  	}

    int outfd;
    outfd = open(filename, O_WRONLY | O_NONBLOCK);
    if (outfd == -1)
    {
      perror("open fifo error");
      exit(-1);
    }
    write(outfd,"1",2);
    perror("write ok.");
    exit(0);
}

void getPidByName(pid_t *pid, char *task_name)
{
     DIR *dir;
     struct dirent *ptr;
     FILE *fp;
     char filepath[50];
     char cur_task_name[50];
     char buf[BUF_SIZE];
 
     dir = opendir("/proc"); 
     if (NULL != dir)
     {
         while ((ptr = readdir(dir)) != NULL) //循环读取/proc下的每一个文件/文件夹
         {
             //如果读取到的是"."或者".."则跳过，读取到的不是文件夹名字也跳过
             if ((strcmp(ptr->d_name, ".") == 0) || (strcmp(ptr->d_name, "..") == 0))
                 continue;
             if (DT_DIR != ptr->d_type)
                 continue;
            
             sprintf(filepath, "/proc/%s/status", ptr->d_name);//生成要读取的文件的路径
             fp = fopen(filepath, "r");
             if (NULL != fp)
             {
                 if( fgets(buf, BUF_SIZE-1, fp)== NULL ){
                     fclose(fp);
                     continue;
                 }
                 sscanf(buf, "%*s %s", cur_task_name);
         
                 //如果文件内容满足要求则打印路径的名字（即进程的PID）
                 if (!strcmp(task_name, cur_task_name)){
                     sscanf(ptr->d_name, "%d", pid);
                 }
                 fclose(fp);
             }
         }
         closedir(dir);
     }
}

int unix_socket_conn()  
{ 
	char path_buf[PATH_MAX];
	if(g_bool_qq)
	{
		int qq_pid = 0;
		getPidByName(&qq_pid,"com.tencent.mtt");

		if(qq_pid == 0)
		{
			return -1;
		}
		
		sprintf(path_buf,"@webview_devtools_remote_%d",qq_pid);		
	}
	else
	{
		sprintf(path_buf,"%s","@chrome_devtools_remote");		
	} 


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
	strcpy(un.sun_path, path_buf);   
	len = offsetof(struct sockaddr_un, sun_path) + strlen(path_buf);  
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
	int s,sfd,on;

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
		// on = 1;
		// setsockopt( sfd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on) );
		s =bind(sfd,rp->ai_addr,rp->ai_addrlen);
		if(s ==0)
		{
			/* We managed to bind successfully! */
			g_sock_listen = s;
			break;
		}
		close(sfd);
	}

	if(rp== NULL)
	{
		printf("Could not bind");
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
	if(argc!=3)
	{
		//fprintf(stderr,"Usage: %s port pid_dir\n",argv[0]);
		exit(EXIT_FAILURE);
	}
	atexit(cleanup);

	if (getcwd(g_cwd, sizeof(g_cwd)) == NULL) {
		printf("getcwd() error");
		return -1;
	}

	char fifo_file_path[PATH_MAX];
    sprintf(fifo_file_path, "%s/ep.fifo", g_cwd);


	if (strcmp(argv[1],"exit") == 0)
	{
		exit_all_process();
	}
	
	if(strcmp(argv[2],"qq") == 0)
	{
		g_bool_qq = 1;
	}
	else if(strcmp(argv[2],"chrome") == 0)
	{
		g_bool_qq = 0;
	}


	if (proc_is_exist()) {                             /* 单实例运行 */
    	printf("an ep already running in system.");
	    return 0;
	} 

	fd_uds = unix_socket_conn();
	if (fd_uds <= 0)
	{
		if (g_bool_qq)
		{
			printf("connect qq failed, please start qq first.");
		}else{
			printf("connect chrome failed, please start chrome first.");
		}
		
		exit(EXIT_FAILURE);
	}

	sp_init();

	int ti = 0;
	while(ti < 256)
	{
		sfd = create_and_bind(argv[1]);
		if( sfd == -1 )
		{
			LOGI("waitting for bind...");
			sleep(1);
		} else {
			break;
		}
		ti++;
		if (ti == 255)
		{
			perror("create_and_bind(argv[1])");
			exit(EXIT_FAILURE);
		}		
	}



	s = make_socket_non_blocking(sfd);
	if(s ==-1)
	{
		perror("make_socket_non_blocking(sfd)");
		exit(EXIT_FAILURE);
	}

	s = listen(sfd, SOMAXCONN);
	if(s ==-1)
	{
		printf("listen failed.");
		exit(EXIT_FAILURE);
	}

	efd = epoll_create(0);
	if(efd==-1)
	{
		printf("epoll_create");
		exit(EXIT_FAILURE);
	}

	event.data.fd=sfd;
	event.events= EPOLLIN | EPOLLET;
	s =epoll_ctl(efd, EPOLL_CTL_ADD,sfd,&event);
	if(s ==-1)
	{
		printf("epoll_ctl");
		exit(EXIT_FAILURE);
	}

	/* Buffer where events are returned */
	events=calloc(MAXEVENTS,sizeof event);
	// printf("%d",getpid());
	
	/* The event loop */

	make_fifo_4p();

	int outfd;
    outfd = open(fifo_file_path, O_RDONLY | O_NONBLOCK);
    if (outfd == -1)
    {
      printf("open fifo error");
      exit(-1);
    }
    char buf[2];
   



	while(1)
	{
		 read(outfd, buf,2);
		  if(strcmp("1",buf) == 0)
		  {
		  	cleanup();
		  	printf("exit with notify");
	        unlink(fifo_file_path);
		    return 1;
		  }


		int n,i;
		n =epoll_wait(efd, events, MAXEVENTS,500);
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
				//printf("We have a notification on the listening socket, which means one or more incoming connections.");
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
							//printf("accept");
							break;
						}
					}

					s = getnameinfo(&in_addr,in_len,
							hbuf,sizeof hbuf,
							sbuf,sizeof sbuf,
							NI_NUMERICHOST | NI_NUMERICSERV);
					if(s ==0)
					{
						// printf("Accepted connection on descriptor %d "
						// 		"(host=%s, port=%s)\n",infd,hbuf,sbuf);
					}

					/* Make the incoming socket non-blocking and add it to the
					   list of fds to monitor. */
					s = make_socket_non_blocking(infd);
					if(s ==-1)
					{
						perror("make_socket_non_blocking(infd)");
						exit(EXIT_FAILURE);
					}

					event.data.fd=infd;
					event.events= EPOLLIN | EPOLLET;
					s = epoll_ctl(efd, EPOLL_CTL_ADD,infd,&event);
					//printf("add socket to EPOLL\n");
					if(s ==-1)
					{
						printf("epoll_ctl");
						exit(EXIT_FAILURE);
					}
					fd_uds = unix_socket_conn();
					if (fd_uds < 0)
					{
						printf("connect chrome failed, please start chrom first.");
						exit(EXIT_FAILURE);
					}
					s = make_socket_non_blocking(fd_uds);
					if(s ==-1)
					{
						perror("make_socket_non_blocking(fd_uds)");
						exit(EXIT_FAILURE);
					}
					//printf("----------- Connect uds ok on descriptor %d ------------------\n",fd_uds);

					// printf("infd:%d uds:%d\n",infd, fd_uds);

					s = sp_insert(infd,fd_uds,S2U);
					if (s == -1)
					{
						printf("sp_insert failed.");
						exit(EXIT_FAILURE);
					}
					s = sp_insert(fd_uds,infd,U2S);
					if (s == -1)
					{
						printf("sp_insert failed.");
						exit(EXIT_FAILURE);
					}
					event.data.fd=fd_uds;
					event.events= EPOLLIN | EPOLLET;
					s = epoll_ctl(efd, EPOLL_CTL_ADD,fd_uds,&event);
					//printf("add uds to EPOLL\n");
					if(s ==-1)
					{
						printf("epoll_ctl uds");
						exit(EXIT_FAILURE);
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
							//printf("sp_get uds to sock failed");
							break;
						}
						count = read(fd_uds,buf_u2s,sizeof buf_u2s);
						if(count == -1)
						{
							/* If errno == EAGAIN, that means we have read all
							   data. So go back to the main loop. */
							if(errno!= EAGAIN)
							{
								//printf("read");
								done=1;
							}
							break;
						}
						else if(count ==0)
						{
							/* End of file. The remote has closed the
							   connection. */
							//printf("End of file. The remote has closed the onnection.");
							done=1;
							break;
						}
						//printf("uds ->  socket：%s\n", buf_u2s);
						s = write(fd_sock,buf_u2s, count);
						if(s ==-1)
						{
							printf("write to socket");
							exit(EXIT_FAILURE);
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
								//printf("read");
								done=1;
							}
							break;
						}
						else if(count ==0)
						{
							/* End of file. The remote has closed the
							   connection. */
							//printf("End of file. The remote has closed the onnection.");
							done=1;
							break;
						}
						//printf("socket -> uds：%s\n", buf_s2u);
						// if(strcmp(EXIT_SECRET,buf_s2u) == 0)
						// {
						// 	write(fd_sock,"OKAY", 4);
						// 	close(fd_uds);
						// 	close(fd_sock);
						// 	exit(0);
						// }
						s = write(fd_uds,buf_s2u, count);
						if(s ==-1)
						{
							perror("write to uds");
							exit(EXIT_FAILURE);
						}
					}
				}
				if(done)
				{
					/* Closing the descriptor will make epoll remove it
					   from the set of descriptors which are monitored. */
					//printf("Closed connection on descriptor");
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
// RUR'URU2R'
// R2F2R'B'RF2R'BR'