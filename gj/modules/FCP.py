# This file contains code fragments ripped from the mighty fish.
# Thank you fish.
#

import string, glob, socket, os, fcp_helpers
from fcp_helpers import *;

############################################################
# FCP Server dependant constants
FCP_HEADER_BYTES="\x00\x00\x00\x02"

# UNDOCUMENTED
# Causes FCP_ClientGet and FCP_ClientPut
# to delete the local copy of the key from
# the node's data store before executing.
# see freenet.message.client.ClientRequest
FLAG_DELETELOCAL = 0x01

# MetadataHint kind constants
# defined in freenet.message.client.MetadataHint
MDH_DATA = 1
MDH_REDIRECT = 2
MDH_DATEREDIRECT = 3
MDH_SPLITFILE = 4
MDH_TOODUMB = 5
MDH_ERROR = 6

# Maximum size for non-CHK keys
MAX_SVK_SIZE = 32768

############################################################
# Wrappers for FCP primatives
############################################################

# returns the reply message.
# msg[0] == "NodeHello"
# msg[0] == "Failed" otherwise;
def FCP_ClientHello(server, port):
        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"ClientHello\n"
        dataString=dataString+"EndMessage\n"

	# open the socket
        s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
                raise IOError
        
	s.send(dataString)
        
	# Get response.
        file = s.makefile("r")
        msg = read_message(file)

        file.close()
        s.close()
        
        if msg == None:
                return make_fake_msg('Failed', "Couldn't read reply.")

        
        if msg[0] == 'NodeHello' and msg[1].has_key('Node') and  msg[1].has_key('Protocol'):
                return msg
        
        return make_fake_msg('Failed', "Couldn't parse reply.")


# returns successful terminal message on success
def FCP_GenerateCHK(server, port, cipher, inputFileName):
        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"GenerateCHK\n"
        dataString=dataString+"Cipher=" + cipher + "\n"
        dataString=dataString+"EndMessage\n"

	# open the socket
        s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
                raise IOError
        
	s.send(dataString)

        # Write the data
        length = os.stat(inputFileName)[6]
        inputFile = open(inputFileName, "r")
        file = s.makefile("w")
        nWritten = copy_binary(file, length, inputFIle)
        file.close()
        inputFile.close();

        if nWritten != length:
                print "Couldn't write all the data!"
                raise IOError
        
	# Get response.
        file = s.makefile("r")
        msg = read_message(file)
        
        file.close()
        s.close()

        if msg == None:
                return make_fake_msg('Failed', "Couldn't read reply.")

        return msg



# returns A MetadataHint msg if bHint is true and the request succeeds
#         A Success msg if bHint is false and the request succeeds
#         A failure message otherwise.
def FCP_ClientGet(server, port, uri, htl, dataFileName, metaDataFileName, bHint, hintTime, flags):
        # REDFLAG: remove debugging...
        #print "*** FCP_ClientGet ***"
        #print "   server           : ", server
        # segv's printing the port.  wtf?
        #print "   port             : ", port
        #print "   uri              : ", uri
        #print "   htl              : ", htl
        #print "   dataFileName     : ", dataFileName
        #print "   metaDataFileName : ", metaDataFileName
        #print "   bHint            : ", bHint
        #print "   flags            : ", flags
        
        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"ClientGet\n"
        dataString=dataString+"URI=" + uri +"\n"
        dataString=dataString+"HopsToLive=" + str(htl) +"\n"
        if bHint:
                dataString=dataString+"MetadataHint=true\n"
                if hintTime != None:
                        dataString=dataString+"RedirectTimeSec=" + str(hintTime) + "\n"

        # To support local key deletion
        if flags != None:
                dataString=dataString+"Flags=" + hex_string(flags) +"\n"
                
        dataString=dataString+"EndMessage\n"

        # Open a socket
        s = open_socket(server, port);
	if s is None:
		print "Couldn't connect to FCP port"
                raise IOError

	s.send(dataString)

        file = s.makefile("r")

        hintMsg = None
        errMsg = None
        
        # Total length
        tlen = -1
        # Data length
        dlen = -1
        # Metadata Length
        mlen = -1
        
        msg = read_message(file)

        dataFile = open(dataFileName, 'w')
        metaFile = open(metaDataFileName, 'w')
        
        # Total byte count
        count = 0
        
        while msg != None:
           #print "msg: " + msg[0]     
           if msg[0] == 'DataFound':
              tlen = int(msg[1]['DataLength'], 16)
              mlen = 0
              if msg[1].has_key('MetadataLength'):
                      mlen = int(msg[1]['MetadataLength'], 16)
              dlen = tlen - mlen
           if msg[0] == 'DataChunk':
                   length = int(msg[1]['Length'], 16)
                   if count < mlen:
                      nBytes = length
                      if count + nBytes > mlen:
                         nBytes = mlen
                      nRead = copy_binary(file, nBytes, metaFile)
                      if nRead != nBytes:
                              print "Didn't read enough bytes!"
                              print "nRead: ", nRead, " nBytes: ", nBytes
                              raise IOError
                      count += nRead
                      length -= nRead

                   if count >= mlen:
                      nBytes = length
                      if count + nBytes > tlen:
                         nBytes = tlen - count

                      nRead = copy_binary(file, nBytes, dataFile)

                      if nRead != nBytes:
                              print "Didn't read enough bytes!"
                              print "nRead: ", nRead, " nBytes: ", nBytes
                              raise IOError
                      count += nRead

           # Handle restarts
           if msg[0] == 'Restarted':
                   dataFile.close()
                   metaFile.close()
                   count = 0
                   
                   dataFile = open(dataFileName, 'w')
                   metaFile = open(metaDataFileName, 'w')
                   
           # Handle terminal messages
           if msg[0] == 'DataNotFound' or \
              msg[0] == 'RouteNotFound' or \
              msg[0] == 'URIError' or \
              msg[0] == 'FormatError' or \
              msg[0] == 'Failed':
                   errMsg = msg
                   break
           if msg[0] == 'MetadataHint':
                   hintMsg = msg
                   break
           
           msg = read_message(file)
           

        dataFile.close()
        metaFile.close()
        file.close()        
        s.close()

        if errMsg != None:
                return errMsg

        if hintMsg != None:
                return hintMsg
        
        return make_fake_msg('Success', 'Downloaded successfully.')


# returns a Success message on success
def FCP_ClientPut(server, port, uri, htl, dataFileName, metaDataFileName, cipher, flags):
        # Set up the input files
        dlen = os.stat(dataFileName)[6]
        mlen = os.stat(metaDataFileName)[6]
        tlen = dlen + mlen

        assert tlen > 0

        dataFile = open(dataFileName, 'r')
        metaFile = open(metaDataFileName, 'r')

        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"ClientPut\n"
        dataString=dataString+"URI=" + uri +"\n"
        dataString=dataString+"HopsToLive=" + hex_string(htl) +"\n"
        dataString=dataString+"DataLength=" + hex_string(tlen) +"\n"
        if mlen > 0:
                dataString=dataString+"MetadataLength=" + hex_string(mlen) +"\n"

        # To support local key deletion
        if flags != None:
                dataString=dataString+"Flags=" + hex_string(flags) +"\n"

        if cipher != None:
                dataString=dataString+"Cipher=" + cipher +"\n"
        
        dataString=dataString+"Data\n"

        #print "ClientPut request:"
        #print dataString
        
	# open the socket
	s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
		raise IOError

        #Send the message
	s.send(dataString)

        # Send the trailing data
        file = s.makefile("w")
        nWritten = copy_binary(metaFile, mlen, file)
        if nWritten != mlen:
                print "Couldn't write all the metadata!"
                raise IOError
        file.flush()
        nWritten = copy_binary(dataFile, dlen, file)
        if nWritten != dlen:
                print "Couldn't write all the data!"
                raise IOError
        file.flush()

        file.close()

        #hmmmm
        file = s.makefile("r")
        
        # REDFLAG: ClientPut never re-requests data? i.e. Restart?
        
        # Release the input files. We don't need them anymore.
        dataFile.close()
        metaFile.close()
                
        # Now read the reply off of the socket.
        errMsg = None
        
        msg = read_message(file)
        
        while msg != None:
           #print "msg: " + msg[0]     
                   
           # Handle terminal messages
           if msg[0] == 'KeyCollision' or \
              msg[0] == 'RouteNotFound' or \
              msg[0] == 'URIError' or \
              msg[0] == 'SizeError' or \
              msg[0] == 'FormatError' or \
              msg[0] == 'Failed':
                   errMsg = msg
                   break

           # Success
           if msg[0] == 'Success':
                   file.close()
                   s.close()
                   return msg
           
           msg = read_message(file)
           

        file.close()        
        s.close()

        if errMsg != None:
                return errMsg

        # REDFLAG: how could we get here?
        
        return make_fake_msg('Failed', "Don\'t know what happend!")


############################################################
# Forward Error Correction (FEC) FCP functions
############################################################

# returns a list of SegmentHeader messages on success.
def FCP_FECSegmentFile(server, port, algorithm, fileLength):
        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"FECSegmentFile\n"
        dataString=dataString+"AlgoName=" + algorithm + "\n"
        dataString=dataString+"FileLength=" + hex_string(fileLength) + "\n"
        dataString=dataString+"EndMessage\n"

        #print "FECSegmentFile:"
        #print dataString
        
	# open the socket
        s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
		raise IOError
        
	s.send(dataString)
        
	# Get response.
        file = s.makefile("r")

        headers = []
        
        msg = read_message(file)
        while msg != None:
                if msg[0] != 'SegmentHeader':
                        print "Received an unexpected msg!"
                        headers = []
                        headers.append(msg)
                        break;
                
                headers.append(msg)
                msg = read_message(file)
        
        file.close()
        s.close()

        return headers



# returns the list of segment headers and block maps
# required to download a SplitFile on success
# returns None on failure
def FCP_FECSegmentSplitFile(server, port, inputFileName):

        # Get length of the file containing the
        # SplitFile metadata.
        tlen = os.stat(inputFileName)[6]

        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"FECSegmentSplitFile\n"
        dataString=dataString+"DataLength=" + hex_string(tlen) + "\n"
        dataString=dataString+"Data\n"

        print "FECSegmentSplitFile:"
        print dataString
        
	# open the socket
        s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
		raise IOError

        # REDFLAG: bush league, use file interface
	s.send(dataString)

        # Send the metadata.
        file = s.makefile("w");
        inputFile = open(inputFileName, "r")
        copy_binary(inputFile, tlen, file)
        inputFile.close()

	# Get response.
        file = s.makefile("r")

        # Hmmmm.... this is inconsistent
        # Should there be a "SegmentedSplitFile" msg?
        # NO, because messages come back as msgs not data
        
        msg = read_message(file)
        if msg[0] != 'SegmentHeader':
                print "Received an unexpected msg:", msg
                return None

        headers = []
        maps = []
        error = 0
        while msg != None:
                if msg[0] != 'SegmentHeader':
                        print "Received an unexpected msg!"
                        error = 1
                        break
                
                headers.append(msg)

                msg = read_message(file)
                if msg[0] != 'BlockMap':
                        print "Received an unexpected msg!"
                        error = 1
                        break;
                
                maps.append(msg)

                msg = read_message(file)
        
        file.close()
        s.close()

        if error:
                return None

        return (headers, maps)

# Makes SplitFile metadata for a SplitFile with the given
# set of headers and block maps.
# returns non-zero on succcess, 0 otherwise
def FCP_FECMakeMetadata(server, port, headers, maps, description, mimeType, outputFileName):
        #print "*** FCP_FECMakeMetadata"
        #print "   server: ", server
        #print "   port: ", port
        #print "   headers: ", headers
        #print "   maps: ", maps
        #print "   description: ", description
        #print "   mimetype: ", mimeType
        #print "   outputFileName: ", outputFileName

        # REDFLAG: check arguments

        # Build the (header, map) list ahead of time
        # because we need its length for the FECMakeMetadata
        # msg.
        
        listString = ""
        # Write the header block map pairs
        for index in range(len(headers)):        
                header = headers[index]
                # Segment
                listString=listString+"SegmentHeader\n"
                for field in header[1].keys():
                        listString=listString+ field+ "=" + header[1][field] + "\n"
                listString=listString+"EndMessage\n" 

                map = maps[index]
                # Map
                listString= listString + "BlockMap\n"
                for field in map[1].keys():
                        listString=listString+ field+ "=" + map[1][field] + "\n"
                listString=listString+"EndMessage\n" 
        
        tlen = len(listString)
        
        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"FECMakeMetadata\n"
        dataString=dataString+"Segments=" + hex_string(len(headers)) + "\n"
        if description:
                dataString=dataString+"Description=" + description + "\n"
        if mimeType:        
                dataString=dataString+"MimeType=" + mimeType + "\n"
        dataString=dataString+"DataLength=" + hex_string(tlen) +"\n"
        dataString=dataString+"Data\n"

        print "FECMakeMetadata:"
        print dataString
        print "---------------:"
        print listString

        
	# open the socket
        s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
		raise IOError

        # REDFLAG: bush league, use file interface
	s.send(dataString)
        s.send(listString)
        
	# Get response.
        file = s.makefile("r")

        # Hmmmm.... this is inconsistent
        # Should there be a "SegmentedSplitFile" msg?
        # NO, because messages come back as msgs not data

        msg = read_message(file)
        if msg[0] != 'MadeMetadata':
                print "Received an unexpected msg:", msg
                return 0;

        outputFile = open(outputFileName, "w")

        tlen = int(msg[1]['DataLength'], 16)
        count = 0;
        msg = read_message(file)
        while msg != None:
                #print "msg: " + msg[0]     
                if msg[0] == 'DataChunk':
                        length = int(msg[1]['Length'], 16)
                        nRead = copy_binary(file, length, outputFile)
                        if nRead != length:
                                print "Couldn't read enough data!"
                                raise IOError
                        count += nRead
                        print "Reading off socket metadata: " , count
                        if count >= tlen:
                                break
                        
                        msg = read_message(file)
                else:
                        print "Received an unexpected msg:", msg
                        return 0;
        outputFile.close()
        return 1
        

# inputFile is the name of the file containing the entire file to encode.
# The function will seek to the appropriate segment specific offset
# based on the data in header.
#
# returns 1 on success 0 otherwise
def FCP_FECEncodeSegment(server, port, header, inputFileName, checkBlockFileNames):

        dlen = os.stat(inputFileName)[6]

        segment = int(header[1]['SegmentNum'], 16)
        segments = int(header[1]['Segments'], 16)
        
        blockCount = int(header[1]['BlockCount'], 16)
        blockSize = int(header[1]['BlockSize'], 16)
        checkBlockSize = int(header[1]['CheckBlockSize'], 16)
        offset = long(header[1]['Offset'], 16)

        # Reconstruct the SegmentHeader msg
        # so that we can send it in the metadata
        headerString="SegmentHeader\n"
        for field in header[1].keys():
                headerString=headerString+ field+ "=" + header[1][field] + "\n"
        headerString=headerString+"EndMessage\n" 

        dlen = dlen - offset
        if segment < segments - 1:
                if dlen > blockCount * blockSize:
                        # Respect segment size for non
                        # end segments.
                        dlen = blockCount * blockSize

        # IMPORTANT: FEC algorithm can request extra
        #            zero padded blocks to make the
        #            stats. work.
        padBytes = blockCount * blockSize - dlen

        mlen = len(headerString)
        tlen = dlen + padBytes + mlen
        
        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"FECEncodeSegment\n"
        dataString=dataString+"DataLength=" + hex_string(tlen) + "\n"
        dataString=dataString+"MetadataLength=" + hex_string(mlen) + "\n"
        dataString=dataString+"Data\n"

        #print "FECSegmentFile:"
        #print dataString

        #print "SegmentHeader (sent via metadata):"
        #print headerString

	# open the socket
        s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
		raise IOError

        # REDFLAG: errr... don't I have to check that all the bytes were written?
	# s.send(dataString)
        # fix other places.
        
        # Send SegmentHeader in metadata.
        file = s.makefile("w")

        file.write(dataString)
        file.write(headerString)

        # Send data blocks
        inputFile = open(inputFileName, "r")
        inputFile.seek(offset)
        copy_binary(inputFile, dlen, file)

        # Important: Must zero pad to expected length.
        zero_pad(file, padBytes)

        file.flush()
        file.close()

        # Get the confirmation
        file = s.makefile("r")
        msg = read_message(file)

        if msg[0] != 'BlocksEncoded':
                print "Received an unexpected msg:", msg
                return 0
        
        # Read check blocks off the socket.
        blockNum = 0
        currentFile = open(checkBlockFileNames[blockNum], "w")
        count = 0
        msg = read_message(file)
        while msg != None:
                #print "msg: " + msg[0]     
                if msg[0] == 'DataChunk':
                        length = int(msg[1]['Length'], 16)
                        while length > 0:
                                boundry = (blockNum + 1) * checkBlockSize
                                if count < boundry:
                                        # Read into the current block
                                        nBytes = boundry - count
                                        if nBytes > length:
                                                nBytes = length
                                        nRead = copy_binary(file, nBytes, currentFile)
                                        if nRead != nBytes:
                                                print "Didn't read enough bytes!"
                                                print "nRead: ", nRead, " nBytes: ", nBytes
                                                raise IOError
                                        count += nRead
                                        length -= nRead
                                else:
                                        # Advance to the next block    
                                        currentFile.close()
                                        blockNum = blockNum + 1
                                        currentFile = open(checkBlockFileNames[blockNum], "w")
                        msg = read_message(file)
                else:
                        print "Received an unexpected msg:", msg
                        return 0
        

        currentFile.close()

        file.close()
        s.close()

        return 1
        

# returns 1 on success 0 otherwise
def FCP_FECDecodeSegment(server, port, header,
                         blockFileNames,
                         blockIndices, checkIndices, requestedIndices,
                         outputFileNames):


        # REDFLAG: check arguments?
        
        blockCount = int(header[1]['BlockCount'], 16)
        blockSize = int(header[1]['BlockSize'], 16)
        checkBlockCount = int(header[1]['CheckBlockCount'], 16)
        checkBlockSize = int(header[1]['CheckBlockSize'], 16)
        offset = long(header[1]['Offset'], 16)


        #REDFLAG: make client do this themself?
        tmp = []
        for i in checkIndices:
                tmp.append(i + blockCount)

        checkIndices = tmp

        dlen = blockSize * len(blockIndices) + checkBlockSize * len(checkIndices)

        # Reconstruct the SegmentHeader msg
        # so that we can send it in the metadata
        headerString="SegmentHeader\n"
        for field in header[1].keys():
                headerString=headerString+ field+ "=" + header[1][field] + "\n"
        headerString=headerString+"EndMessage\n" 

        mlen = len(headerString)
        tlen = dlen + mlen
        
        # Build the message.
	dataString=FCP_HEADER_BYTES
	dataString=dataString+"FECDecodeSegment\n"
        dataString=dataString+"DataLength=" + hex_string(tlen) + "\n"
        dataString=dataString+"MetadataLength=" + hex_string(mlen) + "\n"
        dataString=dataString+"BlockList=" + hex_index_list(blockIndices) + "\n"
        dataString=dataString+"CheckList=" + hex_index_list(checkIndices) + "\n"
        dataString=dataString+"RequestedList=" + hex_index_list(requestedIndices) + "\n"
        dataString=dataString+"Data\n"

        #print "FECDecodeSegment:"
        #print dataString

        #print "SegmentHeader (sent via metadata):"
        #print headerString

	# open the socket
        s=open_socket(server, port)
	if s is None:
		print "Couldn't connect to FCP port"
		raise IOError

        file = s.makefile("w")

        # Send the FCP decode request.
        file.write(dataString)

        # Send SegmentHeader in metadata.
        file.write(headerString)

        dataSent = 0;
        
        # Send blocks
        index = 0
        for name in blockFileNames:
                fileSize = blockSize
                if index > len(blockIndices):
                        fileSize = checkBlockSize
                        
                assert os.stat(name)[6] == fileSize
                
                inputFile = open(name, "r")
                copy_binary(inputFile, fileSize, file)
                inputFile.close()

                dataSent += fileSize
                index = index + 1

        file.flush()
        file.close()

        # Get the confirmation
        file = s.makefile("r")
        msg = read_message(file)

        if msg[0] != 'BlocksDecoded':
                print "Received an unexpected msg:", msg
                return 0

        # grrrrrrr.... C&P, factor this out.

        # Read decoded data blocks off the socket.
        blockNum = 0
        currentFile = open(outputFileNames[blockNum], "w")
        count = 0
        msg = read_message(file)
        while msg != None:
                #print "msg: " + msg[0]     
                if msg[0] == 'DataChunk':
                        length = int(msg[1]['Length'], 16)
                        while length > 0:
                                boundry = (blockNum + 1) * blockSize
                                if count < boundry:
                                        # Read into the current block
                                        nBytes = boundry - count
                                        if nBytes > length:
                                                nBytes = length
                                        nRead = copy_binary(file, nBytes, currentFile)
                                        if nRead != nBytes:
                                                print "Didn't read enough bytes!"
                                                print "nRead: ", nRead, " nBytes: ", nBytes
                                                raise IOError
                                        count += nRead
                                        length -= nRead
                                else:
                                        # Advance to the next block    
                                        currentFile.close()
                                        blockNum = blockNum + 1
                                        currentFile = open(outputFileNames[blockNum], "w")
                        msg = read_message(file)
                else:
                        print "Received an unexpected msg:", msg
                        return 0
        

        currentFile.close()

        file.close()
        s.close()

        return 1


############################################################
# Automatic insertion and retrieval functions built
# on top of the FCP primatives.
############################################################

############################################################
# Downloads a key from Freenet automatically handling
# redirects.
#
# returns a (success, mimeType, redirectList) tuple
#
# If success is 0, the last entry in the redirectList is the uri that
# couldn't be retrieved.
def GetFreenetKey(server, port, uri, htl, dataFileName, metaDataFileName, hintTime, flags):
        uris = []
        mimeType = None
        
        msg = FCP_ClientGet(server, port, uri, htl, dataFileName, metaDataFileName, 1, hintTime, flags)
        uris.append(uri)
        while msg != None:
                #print "uri: ", uri
                #print "msg: ", msg
                if msg[0] == 'MetadataHint':
                        if mimeType == None and msg[1].has_key('MimeType'):
                                # We take the first mime type definition
                                # in the redirect chain
                                mimeType = msg[1]['MimeType']
                                
                        kind = int(msg[1]['Kind'])
                        # ok to leave as a hex string.
                        hintTime = msg[1]['TimeSec']

                        # Handle metadata using the hint sent
                        # by the FCP server. Cool huh.
                        #
                        if kind == MDH_DATA:
                                # Our work is done.
                                return (1, mimeType, uris)
                                break
                        elif kind == MDH_REDIRECT:
                                uri = msg[1]['NextURI']
                                uris.append(uri)
                                msg = FCP_ClientGet(server, port, uri, htl,
                                                    dataFileName, metaDataFileName, 1, hintTime, flags)
                        elif kind == MDH_DATEREDIRECT:
                                uri = msg[1]['NextURI']
                                # Append increment and offset and evaluation time to dbrs
                                uris.append(uri + " [" + msg[1]['Increment'] +
                                            ", " + msg[1]['Offset'] + ", " + hintTime + "]" )

                                msg = FCP_ClientGet(server, port, uri, htl,
                                                    dataFileName, metaDataFileName, 1, hintTime, flags)
                        elif kind == MDH_SPLITFILE:
                                print "Can't handle splitfiles yet!"
                                break
                        elif kind == MDH_TOODUMB:
                                print "FCP server too dumb to parse the metadata!"
                                break
                        elif kind == MDH_ERROR:
                                print "FCP server enountered and error parsing metadata!"
                                break
                        else:
                                print "Phreaked out. UNKNOWN kind constant: " + str(kind)
                                break
                else:
                        break

        return (0, mimeType, uris)





