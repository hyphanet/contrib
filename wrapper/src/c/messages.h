//
//  Values are 32 bit values layed out as follows:
//
//   3 3 2 2 2 2 2 2 2 2 2 2 1 1 1 1 1 1 1 1 1 1
//   1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0
//  +---+-+-+-----------------------+-------------------------------+
//  |Sev|C|R|     Facility          |               Code            |
//  +---+-+-+-----------------------+-------------------------------+
//
//  where
//
//      Sev - is the severity code
//
//          00 - Success
//          01 - Informational
//          10 - Warning
//          11 - Error
//
//      C - is the Customer code flag
//
//      R - is a reserved bit
//
//      Facility - is the facility code
//
//      Code - is the facility's status code
//
//
// Define the facility codes
//


//
// Define the severity codes
//


//
// MessageId: MSG_EVENT_LOG_MESSAGE
//
// MessageText:
//
//  %2
//
#define MSG_EVENT_LOG_MESSAGE            0x00000064L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM1
//
// MessageText:
//
//  jvm1
//
#define MSG_EVENT_LOG_CATEGORY_JVM1      0x00000001L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM2
//
// MessageText:
//
//  jvm2
//
#define MSG_EVENT_LOG_CATEGORY_JVM2      0x00000002L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM3
//
// MessageText:
//
//  jvm3
//
#define MSG_EVENT_LOG_CATEGORY_JVM3      0x00000003L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM4
//
// MessageText:
//
//  jvm4
//
#define MSG_EVENT_LOG_CATEGORY_JVM4      0x00000004L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM5
//
// MessageText:
//
//  jvm5
//
#define MSG_EVENT_LOG_CATEGORY_JVM5      0x00000005L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM6
//
// MessageText:
//
//  jvm6
//
#define MSG_EVENT_LOG_CATEGORY_JVM6      0x00000006L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM7
//
// MessageText:
//
//  jvm7
//
#define MSG_EVENT_LOG_CATEGORY_JVM7      0x00000007L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM8
//
// MessageText:
//
//  jvm8
//
#define MSG_EVENT_LOG_CATEGORY_JVM8      0x00000008L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVM9
//
// MessageText:
//
//  jvm9
//
#define MSG_EVENT_LOG_CATEGORY_JVM9      0x00000009L

//
// MessageId: MSG_EVENT_LOG_CATEGORY_JVMXX
//
// MessageText:
//
//  jvmxx
//
#define MSG_EVENT_LOG_CATEGORY_JVMXX     0x0000000AL

//
// MessageId: MSG_EVENT_LOG_CATEGORY_WRAPPER
//
// MessageText:
//
//  wrapper
//
#define MSG_EVENT_LOG_CATEGORY_WRAPPER   0x0000000BL

//
// MessageId: MSG_EVENT_LOG_CATEGORY_PROTOCOL
//
// MessageText:
//
//  wrapperp
//
#define MSG_EVENT_LOG_CATEGORY_PROTOCOL  0x0000000CL

