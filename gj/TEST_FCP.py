#!/usr/bin/env python2.2

import string, glob, socket, os, fcp_helpers, FCP
from fcp_helpers import *;
from FCP import *;

# returns the inserted url on success
# returns None on failure
def fec_test_insert_file(fileName, server, port, htl): 
        length = os.stat(fileName)[6]
        
        EMPTY_FILE = "empty.dat"
        file = open(EMPTY_FILE, "w");
        file.close();

        DEVNULL_FILE = "null.dat"
        
        print "Asking FCP server for segmenation info..."
        ret = FCP_FECSegmentFile(server, port, 'OnionFEC_a_1_2', length)

        print "segment headers: "
        print ret
        
        # Keep a lists to use to make the metadata
        maps = []
        headers = []
        for header in ret:
                headers.append(header)
                nChecks = int(header[1]['CheckBlockCount'], 16)
                nBlocks = int(header[1]['BlockCount'], 16)
                nRequired = int(header[1]['BlocksRequired'], 16)
                length = long(header[1]['FileLength'], 16)
                
                checkFiles = make_file_names(range(nChecks), "__check_block_")
                
                print "Creating ", nChecks, " check blocks."
                
                if not FCP_FECEncodeSegment(server, port, header, fileName, checkFiles):
                        print "Couldn't make check blocks."
                        return None;
                
                print "done."
                
                segment_file(fileName, header, "__data_block_")

                print "Inserting data blocks..."

                map = ("BlockMap", {})
                for index in range(nBlocks):
                        blockName = "__data_block_" + str(index)
                        result = FCP_ClientPut(server, port, "freenet:CHK@", htl,
                                               blockName, EMPTY_FILE, None, FLAG_DELETELOCAL)
                        if result == None:
                                print "Insert of data block " + str(index) + " failed."
                                return None
                        elif result[0] == "Success" or \
                             result[0] == "KeyCollision":
                                chk = result[1]['URI']
                                key = "Block." + hex_string(index)
                                map[1][key] = chk
                                print "inserted data block[" + str(index) + "] : " + chk
                        else:
                                print "Unexpected msg!"
                                return None

                print "Inserting check blocks..."
                for index in range(nChecks):
                        blockName = "__check_block_" + str(index)
                        result = FCP_ClientPut(server, port, "freenet:CHK@", htl,
                                               blockName, EMPTY_FILE, None, None)
                        if result == None:
                                print "Insert of check block " + str(index) + " failed."
                                return None
                        elif result[0] == "Success" or \
                             result[0] == "KeyCollision":
                                chk = result[1]['URI']
                                key = "Check." + hex_string(index)
                                map[1][key] = chk
                                print "inserted check block[" + str(index) + "] : " + chk
                        else:
                                print "Unexpected msg!"
                                return None
                        
                        
                maps.append(map)

        print "Making SplitFile metadata..."
        if not FCP_FECMakeMetadata(server, port, headers, maps, "file", "audio/mpg", "__sfmeta.dat"):
                print "Couldn't make the metadata."
                return None

        print "Inserting metadata..."

        result = FCP_ClientPut(server, port, "freenet:CHK@", htl,
                               EMPTY_FILE, "__sfmeta.dat", None, None)
        
        print "Done."
        
        if result[0] == 'Success' or \
           result[0] == 'KeyCollision':
                return result[1]['URI']

        return None


# url can't be redirected.
# for now htl must be 0

def fec_test_request_file(url, fileName, server, port, htl, inputFile):
        EMPTY_FILE = "empty.dat"
        file = open(EMPTY_FILE, "w");
        file.close();

        DEVNULL_FILE = "null.dat"
        
        print "Requesting SplitFile from freenet: " , url

        ret = FCP_ClientGet(server, 8481, url, htl, DEVNULL_FILE, '__dl_meta.dat', 0, None, None)
        if ret[0] != 'Success':
                return 0
        
        print "Getting headers for SplitFile metadata."

        pair = FCP_FECSegmentSplitFile(server, port, '__dl_meta.dat')
        if pair == None:
                print "Couldn't segment SplitFile metadata."
                return 0
                
        headers = pair[0]

        maps = pair[1]

        for index in range(len(headers)):
                header = headers[index]
                map = maps[index]
                
                nRequired = int(header[1]['BlocksRequired'], 16)
                nBlocks = int(header[1]['BlockCount'], 16)
                blockSize = int(header[1]['BlockSize'], 16)

                nChecks = int(header[1]['CheckBlockCount'], 16)
                fileLength = long(header[1]['FileLength'], 16)
                offset = int(header[1]['Offset'], 16)
                segment = int(header[1]['SegmentNum'], 16)
                segments = int(header[1]['Segments'], 16)
                        
                
                # Randomly select required check and data blocks
                indices = make_index_list(nBlocks + nChecks, nRequired)

                dataIndices = []
                checkIndices = []

                for i in indices:
                        if i < nBlocks:
                                dataIndices.append(i)
                        else:
                                checkIndices.append(i - nBlocks)

                print "Requesting data blocks..."
                

                for i in dataIndices:
                        hexIndex =  hex_string(i)
                        key = 'Block.' + hexIndex
                        blockURL = map[1][key]
                        blockFile = '__data_block_' + str(i)
                        ret = FCP_ClientGet(server, 8481, blockURL, htl,
                                            blockFile, DEVNULL_FILE, 0, None, None)

                        if ret == None:
                                print "Couldn't download block: ", i
                                return 0
                        if ret[0] != 'Success':
                                print "Couldn't download block: ", i
                                return 0
                        
                        print "Downloaded block: ", i
                        

                for i in checkIndices:
                        hexIndex =  hex_string(i)
                        key = 'Check.' + hexIndex
                        blockURL = map[1][key]
                        blockFile = '__check_block_' + str(i)
                        ret = FCP_ClientGet(server, 8481, blockURL, htl,
                                            blockFile, DEVNULL_FILE, 0, None, None)

                        if ret == None:
                                print "Couldn't download check block: ", i
                                return 0
                        if ret[0] != 'Success':
                                print "Couldn't download check block: ", i
                                return 0
                        
                        print "Downloaded check block: ", i
                        


                requestedIndices = find_missing_indices(range(nBlocks), dataIndices)

                blockFiles = ( make_file_names(dataIndices, "__data_block_") +
                               make_file_names(checkIndices, "__check_block_") )
                
                reconFiles = make_file_names(requestedIndices, "__recon_block_")
                
                print "FEC decoding..."
                FCP_FECDecodeSegment(server, port, header,
                                     blockFiles,
                                     dataIndices, checkIndices, requestedIndices,
                                     reconFiles)
                

                print "data indices: ", dataIndices
                print "requested Indices: ", requestedIndices
                
                reconList = make_reconstructed_file_list(dataIndices, '__data_block_',
                                                         requestedIndices, '__recon_block_')
                
                print "block file list: "
                print reconList
                
                print "Concatinating blocks...."

                segLen = fileLength
                if segments > 1:
                        if segment < segments - 1:
                                segLen = nBlocks * blockSize
                        else:
                                # final segment
                                segLen = fileLength - offset
                              
                
                # Seeks and appends as nescessary.                
                concatinate_files(reconList, segLen, fileName, segment > 0)



        print "diffing reconstructed file with original..."
        if diff_binary(inputFile, fileName):
                print "They are the same :-)"
                return 1

        return 0
 

def wack_temp_files():
        filz = os.listdir(".")
        for file in filz:
                if len(file) > 2:
                        if file[0:2] == "__":
                                os.remove(file)
                                print "Removed temp file: ", file


############################################################
# Setting up this script:
# 0) This script *WILL DELETE* all files starting with "__" in
#    the current directory  Run it in it's own
#    directory.
# 1) This script can dump a lot of big temp files into the
#    into the current directory. Make sure you have enough
#    disk space.
# 2) Make sure FECTempDir is set in your freenet.conf /
#    freenet.ini.
# 3) Set inputFile to the name of a test file in the current
#    directory.
# 4) reread 0.    
# 5) Comment out the assert line below.
############################################################

assert 0, "Read the config comments at the end of TEST_FCP.py"


inputFile='some_file'

# Note: use a leading __ so it gets cleaned up.

outputFile = "__recon." + inputFile
requestTrials = 1

wack_temp_files()

url = fec_test_insert_file(inputFile, 'localhost', 8481, 0) 

print "fec_test_insert_file inserted splitfile: ", url

for count in range(requestTrials):
        wack_temp_files()
        print "fec_test_request_file iteration: ", count
        if not fec_test_request_file(url, outputFile, 'localhost', 8481, 0, inputFile):
                print "FAILED!"
                break










