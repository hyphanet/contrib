#ifndef FREENET_TRAY_TYPES_H_INCLUDED
#define FREENET_TRAY_TYPES_H_INCLUDED

#include "stdafx.h"

#ifndef __cplusplus
#define __needbool__
#else
#undef __needbool__
#endif


#ifdef __needbool__
typedef enum _tagbool{false=0,true} bool;
#endif

/* internal state enum.  Note - it may be the case that some states are not currently used in the code */
typedef enum _tagfreenetmode
{
	FREENET_STOPPED	= 0,				/* indicates node is not running - has been stopped or has never been started */
	FREENET_RUNNING,					/* indicates node is running */
	FREENET_RESTARTING,					/* indicates node is stopping but that immediately after stoppage will begin starting again */
	FREENET_STOPPING,					/* indicates node is stopping and that after stoppage will end up in the FREENET_STOPPED state */
	FREENET_STARTING,					/* indicates node is stopping and that after stoppage will end up in the FREENET_STOPPED state */
	FREENET_CANNOT_START				/* indicates node is not running and that a recent attempt to start (or restart) the node failed */
} FREENET_MODE;


#endif /*FREENET_TRAY_TYPES_H_INCLUDED*/