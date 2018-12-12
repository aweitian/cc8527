//http://man7.org/linux/man-pages/man7/epoll.7.html
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <errno.h>
#include <signal.h> 
#include <android/log.h>

#include "log.h"

#define MAX_EVENTS 20
#define BUFF_LEN 1024
#define LOG_TAG    "Garri"

#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
static volatile sig_atomic_t proxy_sigquit = 0;
char g_cwd[PATH_MAX];
int epollfd, listenfd;
struct sockaddr_in listenaddr;
typedef struct forward
{
  int fd;
  struct forward *to;
} forward;


static void handlesignal (int sig)
{
	switch (sig)
	{
		case SIGINT:
		case SIGTERM:
		proxy_sigquit++;
		break;
	default:
		break;
	}
}

static void setsignalmasks ()
{
	struct sigaction sa;
	/* Set up the structure to specify the new action. */
	memset (&sa, 0, sizeof (struct sigaction));
	sa.sa_handler = handlesignal;
	sigemptyset (&sa.sa_mask);
	sa.sa_flags = 0;
	sigaction (SIGINT, &sa, NULL);
	sigaction (SIGTERM, &sa, NULL);

	memset (&sa, 0, sizeof (struct sigaction));
	sa.sa_handler = SIG_IGN;
	sa.sa_flags = SA_RESTART;
	sigaction (SIGPIPE, &sa, NULL);
}


int doforward (int from, int to)
{
	printf("starting forwarding...\n");
	char data[BUFF_LEN];
	int rc;
	int wc;
	int n;
	rc = wc = n = 0;
	int fwfd;
	while (1)
	{
		memset (data, '0', BUFF_LEN);
		rc = read (from, data, BUFF_LEN);
		if (rc > 0)
		{
			// Now write this data to the other socket
			wc = rc;
			while (wc > 0)
			{
				n = write (to, data, wc);
				//read函数会返回一个错误EAGAIN，提示你的应用程序现在没有数据可读请稍后再试。
				//
				if (n == -1)
				{
					if ((errno != EAGAIN) && (errno != EWOULDBLOCK))
					{
						return -1;
					}
					printf("remain %d,writen:%d,from:%d,TO:%d\n",wc,n,from,to);
					usleep(50);
				}
				else
				{
					wc -= n;
				}
			}
		}
		else if (rc == 0)		// EOF
		{
			// printf("forwarding EOF:%d\n",errno);
			// if ((errno != EAGAIN) && (errno != EWOULDBLOCK))
			// {
			// 	// printf("Error:[ %s ]\n",strerror(errno) );
			// 	return -1;
			// 	//usleep(10);
			// }
			printf("EOF,from:%d,TO:%d\n",from,to);
			return -1;
		}
		else
		{
			if ((errno != EAGAIN) && (errno != EWOULDBLOCK))
			{
				return -1;
				//usleep(10);
			}
			// All read back to main loop
			//printf("All read back to main loop\n");
			return 0;
		}
	}
}

int init_cwd()
{
	if (getcwd(g_cwd, sizeof(g_cwd)) == NULL) 
	{
		printf("getcwd() error");
		g_cwd[0] = 0;
		return -1;
	}	
	return 0;
}

void init_log()
{
	char log_path[PATH_MAX];
	sprintf(log_path,"%s/qep.log",g_cwd);
	FILE * log_f = fopen( log_path, "a");
	log_set_fp(log_f);
	log_set_quiet(1);
}

void close_log()
{
	log_close_fp();
}

int setnonblocking (int fd)
{
	int oldflags = fcntl (fd, F_GETFL, 0);
	/* If reading the flags failed, return error indication now. */
	if (oldflags == -1)
		return -1;
	/* Set just the flag we want to set. */
	oldflags |= O_NONBLOCK;
	/* Store modified flag word in the descriptor. */
	return fcntl (fd, F_SETFL, oldflags);
}

int dolisten (int *s)
{
	int rc;
	int temps;
	temps = *s;
	rc = listen (temps, 10);	/* backlog of 10 */
	return rc;
}


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
	strcpy(un.sun_path, servername);   
	len = offsetof(struct sockaddr_un, sun_path) + strlen(servername);  
	un.sun_path[0] = 0; 
	if (connect(fd, (struct sockaddr *)&un, len) < 0)   
	{  
		return -1;   
	}   
	else  
	{  
		return fd;  
	}  
} 



void runpoller ()
{
	struct epoll_event ev, events[MAX_EVENTS];
	int nfds, n;
	int accept_fd;
	int new_fd;
	struct sockaddr_in addr;
	socklen_t alen = sizeof addr;

	while(1)
	{
		//When successful, epoll_wait() returns the number of file descriptors
		//ready for the requested I/O, or zero if no file descriptor became
		//ready during the requested timeout milliseconds.  When an error
		//occurs, epoll_wait() returns -1 and errno is set appropriately.
		nfds = epoll_wait (epollfd, events, MAX_EVENTS, 2000);
		//printf ("FD wake %d\n", nfds);
		if (nfds == -1)
		{
			if ((errno != EAGAIN) && (errno != EWOULDBLOCK))
			{
				log_error ("epoll_wait");
				exit (EXIT_FAILURE);
			}
			usleep(50);
			continue;
		}
		if (proxy_sigquit > 0)
		{
			close(listenfd);
			break;
		}
		for (n = 0; n < nfds; n++)
		{
			if((events[n].events & EPOLLERR))
			{
				ev = events[n];
				forward *fw;
				fw = (forward *) ev.data.ptr;

				perror ("EPOLLERR");
				epoll_ctl(epollfd, EPOLL_CTL_DEL, fw->fd, (void *)1L);
				epoll_ctl(epollfd, EPOLL_CTL_DEL, fw->to->fd, (void *)1L);
				close (fw->fd);
				close (fw->to->fd);
				free (fw->to);
				free (fw);

				continue;
			}
			else if(events[n].events & EPOLLHUP)
			{
				printf("EPOLLHUP\n" );
				continue;
			}
			else if ((events[n].events & EPOLLIN) && events[n].data.fd == listenfd)
			{
				// new connection
				//printf("new connection...\n");
				accept_fd = accept (listenfd, ((struct sockaddr *) &addr), &alen);
				if (accept_fd == -1)
				{
					if ((errno != EAGAIN) && (errno != EWOULDBLOCK))
					{
						perror ("accept");
						exit (EXIT_FAILURE);
					}
					continue;
				}
				//printf ("Accept done %d\n", accept_fd);
				if(setnonblocking (accept_fd) == -1)
				{
					perror("setnonblocking for accept fd failed.");
					exit (EXIT_FAILURE);
				}
				new_fd = unix_socket_conn ("@chrome_devtools_remote");
				if (new_fd != -1)
				{
					if(setnonblocking(new_fd) == -1)
					{
						perror("setnonblocking for uds fd failed.");
						exit (EXIT_FAILURE);
					}
					//printf ("Backend connected %d\n", new_fd);
					//ev.events = EPOLLIN | EPOLLET;//EDGE TRIGGER
					ev.events = EPOLLIN;//默认是LT模式
					//ev.data.fd = new_fd;
					forward *afw, *bfw;
					afw = bfw = NULL;
					afw = calloc (1, sizeof (forward));
					afw->fd = new_fd;
					ev.data.ptr = (void *) afw;

					printf("add uds to epoll:%d\n", new_fd);

					if (epoll_ctl (epollfd, EPOLL_CTL_ADD, new_fd, &ev) == 0)
					{
						//printf ("epoll connected %d\n", new_fd);
						// Add the accept fd to epoll 
						//ev.data.fd = accept_fd;
						bfw = calloc (1, sizeof (forward));
						bfw->fd = accept_fd;
						bfw->to = afw;
						afw->to = bfw;
						ev.data.ptr = (void *) bfw;
						if (epoll_ctl (epollfd, EPOLL_CTL_ADD, accept_fd, &ev) != 0)
						{
							perror ("epoll_ctl accept_fd");
							free (bfw);
							free (afw);
							
							epoll_ctl(epollfd, EPOLL_CTL_DEL, accept_fd, (void *)1L);
							close (accept_fd);
							epoll_ctl(epollfd, EPOLL_CTL_DEL, new_fd, (void *)1L);
							close (new_fd);
						}
					}
					else
					{
						// Epoll failed
						perror ("epoll_ctl new_fd");
						free (afw);
						
						epoll_ctl(epollfd, EPOLL_CTL_DEL, accept_fd, (void *)1L);
						close (accept_fd);
						epoll_ctl(epollfd, EPOLL_CTL_DEL, new_fd, (void *)1L);
						close (new_fd);
					}
				}
				else
				{
					perror ("open chrome fd failed");
					epoll_ctl(epollfd, EPOLL_CTL_DEL, accept_fd, (void *)1L);
					close (accept_fd);
				}
			}
			else if(events[n].events & EPOLLIN)
			{
				// forward event
				ev = events[n];
				forward *fw;
				fw = (forward *) ev.data.ptr;
				//printf ("epoll event connected %d -> %d\n", fw->fd, fw->to->fd);
				if (doforward (fw->fd, fw->to->fd) == -1)
				{
					perror ("Cleaning fd");

					epoll_ctl(epollfd, EPOLL_CTL_DEL, fw->fd, (void *)1L);
					close (fw->fd);
					epoll_ctl(epollfd, EPOLL_CTL_DEL, fw->to->fd, (void *)1L);
					close (fw->to->fd);

					free (fw->to);
					free (fw);
				}
			}
			else
			{
				perror("unknown event\n");
			}
		}
	}
	close (epollfd);
}




void do_exit()
{
	close_log();
	exit(-1);
}

int main(int argc,char*argv[])
{
	if (init_cwd() != 0)
	{
		printf("%s\n", "Listener socket error");
		do_exit ();
	}

	init_log();

	setsignalmasks ();
	epollfd = epoll_create (10);
	if (epollfd == -1)
	{
		printf("Epoll create error");
		do_exit ();
	}
	listenfd = socket( AF_INET, SOCK_STREAM, 0 );   /* 建立socket */
	if (!(listenfd > 0))
	{
		printf("Listener socket error");
		do_exit ();
	}
	
	memset( &listenaddr, 0,sizeof listenaddr);	/*将其他属性置0*/
	listenaddr.sin_family = AF_INET;	/*该属性表示接收本机或其他机器传输*/
	listenaddr.sin_port	= htons( 23764 );	/*端口号*/
	listenaddr.sin_addr.s_addr = htonl( INADDR_ANY );	/*IP，括号内容表示本机IP*/

	//I've run into that same issue as well. 
	//It's because you're closing your connection to the socket, 
	//but not the socket itself. The socket can enter a TIME_WAIT state 
	//(to ensure all data has been transmitted, TCP guarantees delivery if possible) 
	//and take up to 4 minutes to release.
	int tc = 0;
	while(tc < 256)
	{
		int bfd = bind (listenfd, (struct sockaddr *) &listenaddr, sizeof(struct sockaddr));
		if (bfd == -1)
		{
			tc++;
			if(tc < 256)
			{
				printf("23764 port in used:%s,try %d times,Sleep 1 s...\n",strerror(errno),tc);
				fflush(stdout);
				sleep(1);
			}
			else
			{
				printf("bind failed.exiting...\n");
				do_exit ();
			}
		}else{
			break;
		}		
	}


	if (dolisten (&listenfd) == -1)
	{
		printf("Error on listen:%s",strerror(errno));
		do_exit ();
	}

	// Now set socket to epoll
	static struct epoll_event event;
	event.events = EPOLLIN | EPOLLERR;
	event.data.fd = listenfd;

	if (epoll_ctl (epollfd, EPOLL_CTL_ADD, listenfd, &event) == -1)
	{
		printf("Error adding listerner to epoll");
		do_exit ();
	}
	runpoller ();


	close_log();
	return 0;
}
