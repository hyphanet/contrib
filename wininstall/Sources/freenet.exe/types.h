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

typedef enum _tagfreenetmode
{
	FREENET_STOPPED	= 0,
	FREENET_RUNNING,
	FREENET_RESTARTING,
	FREENET_STOPPING,
	FREENET_CANNOT_START
} FREENET_MODE;


#endif /*FREENET_TRAY_TYPES_H_INCLUDED*/