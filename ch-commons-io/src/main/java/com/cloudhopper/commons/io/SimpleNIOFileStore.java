package com.cloudhopper.commons.io;

/*
 * #%L
 * ch-commons-io
 * %%
 * Copyright (C) 2012 - 2013 Cloudhopper by Twitter
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.NoSuchAlgorithmException;

import com.cloudhopper.commons.util.Hasher;
import com.cloudhopper.commons.util.Hasher.Algorithm;

/**
 * A simple implementation of FileStore that uses NIO FileChannel direct ByteBuffer-ing, and
 * stores the file using a simple hash directory structure.
 * @author garth
 */
public class SimpleNIOFileStore 
    implements FileStore
{

    private static final int B16K = 16*1024;

    public SimpleNIOFileStore(IdGenerator idGen, String base) throws SecurityException {
	this.idGen = idGen;
	this.baseDir = new File(base);
	if (!baseDir.exists()) baseDir.mkdirs();
    }

    private IdGenerator idGen;
    private File baseDir;

    @Override
    public Id write(InputStream is) throws FileStoreException
    {
	return write(Channels.newChannel(is));
    }

    @Override
    public Id write(ReadableByteChannel channel) throws FileStoreException
    {
	File f = null;
	RandomAccessFile randomAccessFile = null;
	FileChannel fileChannel = null;
	Id id = idGen.newId();
	try {
	    f = createFile(id.getName());
	    randomAccessFile = new RandomAccessFile(f, "rw");
	    fileChannel = randomAccessFile.getChannel();
	    channelCopyToFile(channel, fileChannel);
	} catch (IOException e) {
	    throw new FileStoreException(e);
	} finally {
	    try {
		if (fileChannel != null) fileChannel.close();
	    } catch (Exception e) {}
	    try {
		if (randomAccessFile != null) randomAccessFile.close();
	    } catch (Exception e) {}
	}
	return id;
    }

    @Override
    public InputStream readStream(Id id) throws FileStoreException
    {
	return Channels.newInputStream(getFileChannel(id));
    }

    @Override
    public ReadableByteChannel readChannel(Id id) throws FileStoreException
    {
	return getFileChannel(id);
    }

    @Override
    public void transferToOutputStream(OutputStream os, Id id) throws FileStoreException
    {
	FileChannel fileChannel = null;
	try {
	    fileChannel = getFileChannel(id);
	    channelCopyFromFile(fileChannel, Channels.newChannel(os));
	} catch (IOException e) {
	    throw new FileStoreException(e);
	} finally {
	    try {
		if (fileChannel != null) fileChannel.close();
	    } catch (Exception e) {}
	}
    }

    @Override
    public void transferToChannel(WritableByteChannel channel, Id id) throws FileStoreException
    {
	FileChannel fileChannel = null;
	try {
	    fileChannel = getFileChannel(id);
	    channelCopyFromFile(fileChannel, channel);
	} catch (IOException e) {
	    throw new FileStoreException(e);
	} finally {
	    try {
		if (fileChannel != null) fileChannel.close();
	    } catch (Exception e) {}
	}
    }

    @Override
    public FileChannel getFileChannel(Id id) throws FileStoreException
    {
	try {
	    File f = getFile(id.getName());
	    RandomAccessFile randomAccessFile = new RandomAccessFile(f, "r");
	    FileChannel fileChannel = randomAccessFile.getChannel();
	    return fileChannel;
	} catch (IOException e) {
	    throw new FileStoreException(e);
	}
    }

    @Override
    public void remove(Id id) throws FileStoreException
    {
	try {
	    File f = getFile(id.getName());
	    f.delete();
	} catch (IOException e) {
	    throw new FileStoreException(e);
	}
    }


    ///////
    // Utilities for file and path
    ///////
    private void channelCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException
    {
	final ByteBuffer buffer = ByteBuffer.allocateDirect(B16K);
	while (src.read(buffer) != -1) {
	    buffer.flip();
	    dest.write(buffer);
	    buffer.compact();
	}
	buffer.flip();
	while (buffer.hasRemaining()) {
	    dest.write(buffer);
	}
    }
 
    private void channelCopyToFile(final ReadableByteChannel src, final FileChannel dest) throws IOException
    {
	long position = 0;
	long transferred = 0;
	do {
	    transferred = dest.transferFrom(src, position, B16K);
	    position += transferred;
	} while (transferred >= B16K);
    }

    private void channelCopyFromFile(final FileChannel src, final WritableByteChannel dest) throws IOException
    {
	long position = 0;
	long transferred = 0;
	do {
	    transferred = src.transferTo(position, B16K, dest);
	    position += transferred;
	} while (transferred >= B16K);
    }
 
   private File createFile(String name) throws SecurityException
    {
	File tmpDir = new File(this.baseDir, getHashPathForLevel(name, 2));
	if (!tmpDir.exists()) tmpDir.mkdirs();
	File tmpFile = new File(tmpDir, name);
	if (tmpFile.exists()) throw new SecurityException("This file already exists. Use getFile() to use it.");
	return tmpFile;
    }

    private File getFile(String name) throws IOException
    {
	File tmpDir = new File(this.baseDir, getHashPathForLevel(name, 2));
	if (!tmpDir.exists()) throw new FileNotFoundException("Could not find directory at "+tmpDir.getPath());
	File tmpFile = new File(tmpDir, name);
	if (!tmpFile.exists()) throw new FileNotFoundException("Could not find file at "+tmpFile.getPath());
	return tmpFile;
    }

    private String getHashPathForLevel(String name, int levels)
    {
	String path = "";
	if (levels != 0) {
	    try {
		Hasher hasher = new Hasher(Algorithm.MD5);
		String hash = hasher.toHashedHexString(name);
		path = "";
		for (int i = 1; i <= levels; i++) {
		    path += hash.substring( 0, i ) + "/";
		}
	    } catch (NoSuchAlgorithmException e) {}
	}
	return path;
    }

}