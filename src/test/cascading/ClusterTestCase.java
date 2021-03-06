/*
 * Copyright (c) 2007-2010 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
 *
 * Cascading is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cascading is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Cascading.  If not, see <http://www.gnu.org/licenses/>.
 */

package cascading;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import cascading.flow.Flow;
import cascading.flow.MultiMapReducePlanner;
import cascading.scheme.SequenceFile;
import cascading.scheme.TextLine;
import cascading.tap.Hfs;
import cascading.tuple.Fields;
import cascading.tuple.TupleEntryIterator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MiniMRCluster;

/**
 *
 */
public class ClusterTestCase extends CascadingTestCase
  {
  public static final String CLUSTER_TESTING_PROPERTY = "test.cluster.enabled";

  transient private static MiniDFSCluster dfs;
  transient private static FileSystem fileSys;
  transient private static MiniMRCluster mr;
  transient private static JobConf jobConf;
  transient private static Map<Object, Object> properties = new HashMap<Object, Object>();
  transient private boolean enableCluster;

  int numMapTasks = 4;
  int numReduceTasks = 1;

  private String logger;

  public ClusterTestCase( String string, boolean enableCluster )
    {
    super( string );

    if( !enableCluster )
      this.enableCluster = false;
    else
      this.enableCluster = Boolean.parseBoolean( System.getProperty( CLUSTER_TESTING_PROPERTY, Boolean.toString( enableCluster ) ) );

    this.logger = System.getProperty( "log4j.logger" );
    }

  public ClusterTestCase( String string, boolean enableCluster, int numMapTasks, int numReduceTasks )
    {
    this( string, enableCluster );
    this.numMapTasks = numMapTasks;
    this.numReduceTasks = numReduceTasks;
    }

  public ClusterTestCase( String string )
    {
    super( string );
    }

  public ClusterTestCase()
    {
    }

  public boolean isEnableCluster()
    {
    return enableCluster;
    }

  public void setUp() throws IOException
    {
    if( jobConf != null )
      return;

    if( !enableCluster )
      {
      jobConf = new JobConf();
      }
    else
      {
      System.setProperty( "test.build.data", "build" );
      new File( "build/test/log" ).mkdirs();
      System.setProperty( "hadoop.log.dir", "build/test/log" );
      Configuration conf = new Configuration();

      dfs = new MiniDFSCluster( conf, 4, true, null );
      fileSys = dfs.getFileSystem();
      mr = new MiniMRCluster( 4, fileSys.getUri().toString(), 1 );

      jobConf = mr.createJobConf();

      jobConf.set( "mapred.child.java.opts", "-Xmx512m" );
      jobConf.setMapSpeculativeExecution( false );
      jobConf.setReduceSpeculativeExecution( false );
      }

    jobConf.setNumMapTasks( numMapTasks );
    jobConf.setNumReduceTasks( numReduceTasks );

    if( logger != null )
      properties.put( "log4j.logger", logger );

    Flow.setJobPollingInterval( properties, 500 ); // should speed up tests
    MultiMapReducePlanner.setJobConf( properties, jobConf );
    }

  public Map<Object, Object> getProperties()
    {
    return new HashMap<Object, Object>( properties );
    }

  public JobConf getJobConf()
    {
    return new JobConf( jobConf );
    }

  public FileSystem getFileSystem() throws IOException
    {
    if( fileSys != null )
      return fileSys;

    return FileSystem.get( jobConf );
    }

  public String makeQualifiedPath( String path ) throws IOException
    {
    return new Path( path ).makeQualified( getFileSystem() ).toString();
    }

  protected void copyFromLocal( String inputFile ) throws IOException
    {
    if( !enableCluster )
      return;

    Path path = new Path( inputFile );

    if( !fileSys.exists( path ) )
      FileUtil.copy( new File( inputFile ), fileSys, path, false, jobConf );
    }

  public void tearDown() throws IOException
    {
    // do nothing, let the jvm shut things down
    }

  public void validateLengthText( String path, int length ) throws IOException
    {
    validateLengthText( path, length, null );
    }

  public void validateLengthText( String path, int length, String regex ) throws IOException
    {
    Hfs tap = new Hfs( new TextLine( new Fields( "line" ) ), path );

    assertTrue( "path does not exist: " + path, tap.pathExists( getJobConf() ) );

    TupleEntryIterator iterator = tap.openForRead( getJobConf() );

    try
      {
      validateLength( iterator, length, -1, regex == null ? null : Pattern.compile( regex ) );
      }
    finally
      {
      if( iterator != null )
        iterator.close();
      }
    }

  public void validateLengthSequence( String path, Fields fields, int length ) throws IOException
    {
    validateLengthSequence( path, fields, length, null );
    }

  public void validateLengthSequence( String path, Fields fields, int length, String regex ) throws IOException
    {
    Hfs tap = new Hfs( new SequenceFile( fields ), path );

    assertTrue( "path does not exist: " + path, tap.pathExists( getJobConf() ) );

    TupleEntryIterator iterator = tap.openForRead( getJobConf() );

    try
      {
      validateLength( iterator, length, -1, regex == null ? null : Pattern.compile( regex ) );
      }
    finally
      {
      if( iterator != null )
        iterator.close();
      }
    }
  }
