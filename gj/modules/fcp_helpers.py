import string, os, socket, random

############################################################
# Implementation helper functions
# used by FCP.py and test scripts.
############################################################

def hex_string(number):
        # Explicitly force conversion to long
        # so we know what we need to chop off.
        longNumber = long(number)
        # e.g.
        #0xCAFEBABEL
        #XX        X
        # "cafebabe"

        # Also, force all letters to lowercase.
        hexVal = string.lower(hex(longNumber)[2:-1])
        #print "hex_string: (" , number , ")->", hexVal
        assert long(hexVal, 16) == number
        return hexVal

def hex_index_list(indices):
        ret = ""
        for i in indices:
                ret=ret + hex_string(i) + ","

        ret = ret[:-1]
        return ret



# Opens a socket for an FCP connection.
def open_socket(server, port):
        # REDFLAG: Work around for segv
        # grrrrr.....
        # There is something badly fuXORd with my rh7.1 python2.2
        # install.  People with python properly configured
        # shouldn't have to resort to this.
        portAsString = str(port)
        copyAtNewAddress = int(portAsString)

	# open the socket
	s=None
	for res in socket.getaddrinfo(server, copyAtNewAddress, socket.AF_UNSPEC, socket.SOCK_STREAM):
		af, socktype, proto, canonname, sa = res
		try:
			s = socket.socket(af, socktype, proto)
		except socket.error, msg:
			s = None
			continue
		try:
			s.connect(sa)
		except socket.error, msg:
			s.close()
			s = None
			continue
		break

        return s

# Reads an FCP message off of an open file.
# returns a (Msg Name, Fields Dictionary) tuple
# or None, if no message could be read.
def read_message(file):
        msgName = None     
        fields = {}
        
        buffer = file.readline()
        while buffer != '':
                #print "read_message buffer: ", buffer
                if string.find(buffer, '=') != -1:
                        values = string.split(buffer, '=')
                        fields[values[0]] = string.strip(values[1])
                else:
                        buffer = string.strip(buffer)
                        if msgName == None:
                                msgName = string.strip(buffer)
                        elif buffer == 'EndMessage' or buffer == 'Data':
                                # Stop at the end of the message.
                                break
                        
                buffer = file.readline()


        if msgName == None:
                return None
        
        return (msgName, fields)

def make_fake_msg(msgName, reason):
        fields = {}
        fields['Reason'] = reason
        return (msgName, fields)

# Copy data from a file open for input
# to a file open for output.
#
# Note: This function doesn't close or
#       flush the files.
#
def copy_binary(file, length, outputFile):
        #print "copy_binary: length=", length 
        requested = length
        count = 0
        while length > 0:
                #print "copy_binary: ",  count
                nBytes = 16384
                if nBytes > length:
                        nBytes = length

                buffer = file.read(nBytes)
                if buffer == '':
                        break

                outputFile.write(buffer)
                nWritten = len(buffer)
                count += nWritten
                length -= nWritten
                if nWritten != nBytes:
                        break

        if requested != count:
                print "copy_binary -- only wrote ", count, " of " , requested
                raise IOError
        
        return count

#Output file must be open for writing
def zero_pad(outputFile, length):
        # REDFLAG: hoist out and re-use same buffer?
        # Is this a speed issue?
        buffer = "\x00" * 16384

        while length > 0:
                nBytes = 16384
                if nBytes > length:
                        nBytes = length
                        buffer = "\x00" * nBytes
                        
                outputFile.write(buffer)
                length -= nBytes
                
def same_length(fileNameA, fileNameB):
        if os.stat(fileNameA)[6] == os.stat(fileNameB)[6]:
                return 1
        return 0

def diff_binary(fileNameA, fileNameB):
        fileA = open(fileNameA, "r")
        fileB = open(fileNameB, "r")
        if not same_length(fileNameA, fileNameB):
                print "diff_binary: lengths don't match! ", fileNameA, " ", fileNameB
                return 0

        length = os.stat(fileNameA)[6]
        count = 0

        nBuf = length / 4096
        if length % 4096 != 0:
            nBuf = nBuf + 1

        
        for i in range(nBuf):
            bufferA = fileA.read(4096)
            bufferB = fileB.read(4096)

            nReadA = len(bufferA)
            nReadB = len(bufferB)
            
            if nReadA != nReadB:
                # REDFLAG: underwhelming
                print "My pitiful code choked! Sorry :-("
                assert 0
        
            for j in range(nReadA):
                if bufferA[j] != bufferB[j]:
                    print "Mismatch at byte: " , count
                    return 0
                count = count + 1


        assert count == length

        return 1
        

# Creates data block file for the segment
# of a file corresponding to header, zero
# padding as nescessary.
def segment_file(inputFileName, header, prefix):
        blockCount = int(header[1]['BlockCount'], 16)
        blockSize = int(header[1]['BlockSize'], 16)
        fileLength = long(header[1]['FileLength'], 16)
        offset = long(header[1]['Offset'], 16)

        inputFile = open(inputFileName, "r")
        inputFile.seek(offset)
        
        fullBlocks = (fileLength - offset) / blockSize
        if fullBlocks > blockCount:
                fullBlocks = blockCount

        partialBlock = 0
        if fullBlocks < blockCount:
                partialBlock = 1

        zeroBlocks = 0
        if fullBlocks + partialBlock < blockCount:
                zeroBlocks = blockCount - (fullBlocks + partialBlock)
                
        # Copy full blocks
        for index in range(fullBlocks):
                outputFile = open(prefix + str(index), "w")
                copy_binary(inputFile, blockSize, outputFile)
                outputFile.close()

        # Handle boundry case
        if partialBlock:
                nBytes = fileLength - offset - fullBlocks * blockSize
                padBytes = blockSize - nBytes
                assert padBytes > 0 
                outputFile = open(prefix + str(fullBlocks), "w")
                copy_binary(inputFile, nBytes, outputFile)
                zero_pad(outputFile, padBytes)
                outputFile.close()

        inputFile.close()
        
        # Make zerod blocks
        for index in range(fullBlocks + partialBlock, blockCount):
                outputFile = open(prefix + str(index), "w")
                zero_pad(outputFile, blockSize)
                outputFile.close()
        
                
def concatinate_files(inputFileNames, length, outputFileName, append):
        #print "concatinate_files: length=" , length
        if append:
                outputFile = open(outputFileName, "a")
        else:
                outputFile = open(outputFileName, "w")
                
        index = 0
        flen = os.stat(inputFileNames[index])[6]
        inputFile = open(inputFileNames[index], "r")
        while length > 0:
                # print "index: " , index, "length: ", length
                if flen == 0:
                        inputFile.close()
                        index = index + 1
                        flen = os.stat(inputFileNames[index])[6]
                        inputFile = open(inputFileNames[index], "r")
                nBytes = length
                if nBytes > flen:
                        nBytes = flen
                copy_binary(inputFile, nBytes, outputFile)
                length -= nBytes
                flen -= nBytes
                outputFile.flush()
                # print " outputFile length=", os.stat(outputFileName)[6]

        inputFile.close()
        outputFile.close()

def make_file_names(indices, prefix):
        ret = []
        for i in indices:
                ret.append(prefix + str(i))

        return ret

def make_index_list(max, number):
        if number > max:
                print "Bad arguments number> max"
                assert 0
        
        list = range(max)
        ret = []
        while len(ret) < number:
                element = random.choice(list)
                ret.append(element);
                list.remove(element)
        ret.sort()
        return ret

# makes an ordered list of data block / renonstructed
# block file names
def make_reconstructed_file_list(dataIndices, data_prefix, reconstructedIndices, recon_prefix):
        dataMap = {}
        for index in dataIndices:
                dataMap[index] = 'extant'
                
        reconMap = {}
        for index in reconstructedIndices:
                reconMap[index] = 'extant'

        ret = []
        for index in range(len(dataIndices) + len(reconstructedIndices)):
                if dataMap.has_key(index):
                        ret.append(data_prefix + str(index))
                elif reconMap.has_key(index):
                        ret.append(recon_prefix + str(index))
                else:
                    print "failed, block missing: " , index 
                    assert 0
        return ret

def find_missing_indices(full_range, partial_range):
        list = {}
        for i in partial_range:
                list[i] = "extant"

        ret = []
        for i in full_range:
                if not list.has_key(i):
                        ret.append(i)

        ret.sort()
        return ret
        




