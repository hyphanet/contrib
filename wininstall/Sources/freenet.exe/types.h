#ifndef FREENET_TRAY_TYPES_H_INCLUDED
#define FREENET_TRAY_TYPES_H_INCLUDED

#ifndef __cplusplus
#define __needbool__
#else
#undef __needbool__
#endif


#ifdef __needbool__
typedef enum _tagbool{false=0,true} bool;
#endif

typedef void(__stdcall* LPCONFIGPROC)(void*);

/* internal state enum.  Note - it may be the case that some states are not currently used in the code */
typedef enum _tagfreenetmode
{
	FREENET_STOPPED	= 0,				/* indicates node is not running - has been stopped or has never been started */
	FREENET_RUNNING,					/* indicates node is running */
	FREENET_RUNNING_NO_GATEWAY,			/* indicates node is running but FProxy is not */
	FREENET_RUNNING_NO_INTERNET,		/* indicates node is running but internet connection is 'down' in some way */
	FREENET_RESTARTING,					/* indicates node is stopping but that immediately after stoppage will begin starting again */
	FREENET_STOPPING,					/* indicates node is stopping and that after stoppage will end up in the FREENET_STOPPED state */
	FREENET_CANNOT_START,				/* indicates node is not running and that a recent attempt to start (or restart) the node failed */
	FREENET_NOT_RUNNING_NO_INTERNET		/* indicates node is not running and that the internet connection is 'down' in some way */
} FREENET_MODE;





#endif /*FREENET_TRAY_TYPES_H_INCLUDED*/