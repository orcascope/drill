/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.physical.impl.sort;

import static org.junit.Assert.assertTrue;
import mockit.Injectable;
import mockit.NonStrictExpectations;

import org.apache.drill.common.config.DrillConfig;
import org.apache.drill.common.expression.ExpressionPosition;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.util.FileUtils;
import org.apache.drill.exec.expr.fn.FunctionImplementationRegistry;
import org.apache.drill.exec.memory.BufferAllocator;
import org.apache.drill.exec.ops.FragmentContext;
import org.apache.drill.exec.physical.PhysicalPlan;
import org.apache.drill.exec.physical.base.FragmentRoot;
import org.apache.drill.exec.physical.impl.OperatorCreatorRegistry;
import org.apache.drill.exec.physical.impl.ImplCreator;
import org.apache.drill.exec.physical.impl.SimpleRootExec;
import org.apache.drill.exec.planner.PhysicalPlanReader;
import org.apache.drill.exec.proto.CoordinationProtos;
import org.apache.drill.exec.proto.ExecProtos.FragmentHandle;
import org.apache.drill.exec.rpc.user.UserServer.UserClientConnection;
import org.apache.drill.exec.server.DrillbitContext;
import org.apache.drill.exec.vector.BigIntVector;
import org.apache.drill.exec.vector.IntVector;
import org.junit.AfterClass;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.codahale.metrics.MetricRegistry;

public class TestSimpleSort {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestSimpleSort.class);
  DrillConfig c = DrillConfig.create();
  
  
  @Test
  public void sortOneKeyAscending(@Injectable final DrillbitContext bitContext, @Injectable UserClientConnection connection) throws Throwable{


    new NonStrictExpectations(){{
      bitContext.getMetrics(); result = new MetricRegistry();
      bitContext.getAllocator(); result = BufferAllocator.getAllocator(c);
      bitContext.getOperatorCreatorRegistry(); result = new OperatorCreatorRegistry(c);
    }};
    
    
    PhysicalPlanReader reader = new PhysicalPlanReader(c, c.getMapper(), CoordinationProtos.DrillbitEndpoint.getDefaultInstance());
    PhysicalPlan plan = reader.readPhysicalPlan(Files.toString(FileUtils.getResourceAsFile("/sort/one_key_sort.json"), Charsets.UTF_8));
    FunctionImplementationRegistry registry = new FunctionImplementationRegistry(c);
    FragmentContext context = new FragmentContext(bitContext, FragmentHandle.getDefaultInstance(), connection, null, registry);
    SimpleRootExec exec = new SimpleRootExec(ImplCreator.getExec(context, (FragmentRoot) plan.getSortedOperators(false).iterator().next()));
    
    int previousInt = Integer.MIN_VALUE;

    int recordCount = 0;
    int batchCount = 0;

    while(exec.next()){
      batchCount++;
      IntVector c1 = exec.getValueVectorById(new SchemaPath("blue", ExpressionPosition.UNKNOWN), IntVector.class);
      IntVector c2 = exec.getValueVectorById(new SchemaPath("green", ExpressionPosition.UNKNOWN), IntVector.class);
      
      IntVector.Accessor a1 = c1.getAccessor();
      IntVector.Accessor a2 = c2.getAccessor();
      
      for(int i =0; i < c1.getAccessor().getValueCount(); i++){
        recordCount++;
        assert previousInt <= a1.get(i);
        previousInt = a1.get(i);
        assert previousInt == a2.get(i);
      }
     
      
    }
    
    System.out.println(String.format("Sorted %,d records in %d batches.", recordCount, batchCount));

    if(context.getFailureCause() != null){
      throw context.getFailureCause();
    }
    assertTrue(!context.isFailed());
  }
  
  @Test
  public void sortTwoKeysOneAscendingOneDescending(@Injectable final DrillbitContext bitContext, @Injectable UserClientConnection connection) throws Throwable{


    new NonStrictExpectations(){{
      bitContext.getMetrics(); result = new MetricRegistry();
      bitContext.getAllocator(); result = BufferAllocator.getAllocator(c);
      bitContext.getOperatorCreatorRegistry(); result = new OperatorCreatorRegistry(c);
    }};
    
    
    PhysicalPlanReader reader = new PhysicalPlanReader(c, c.getMapper(), CoordinationProtos.DrillbitEndpoint.getDefaultInstance());
    PhysicalPlan plan = reader.readPhysicalPlan(Files.toString(FileUtils.getResourceAsFile("/sort/two_key_sort.json"), Charsets.UTF_8));
    FunctionImplementationRegistry registry = new FunctionImplementationRegistry(c);
    FragmentContext context = new FragmentContext(bitContext, FragmentHandle.getDefaultInstance(), connection, null, registry);
    SimpleRootExec exec = new SimpleRootExec(ImplCreator.getExec(context, (FragmentRoot) plan.getSortedOperators(false).iterator().next()));
    
    int previousInt = Integer.MIN_VALUE;
    long previousLong = Long.MAX_VALUE;
    
    int recordCount = 0;
    int batchCount = 0;

    while(exec.next()){
      batchCount++;
      IntVector c1 = exec.getValueVectorById(new SchemaPath("blue", ExpressionPosition.UNKNOWN), IntVector.class);
      BigIntVector c2 = exec.getValueVectorById(new SchemaPath("alt", ExpressionPosition.UNKNOWN), BigIntVector.class);
      
      IntVector.Accessor a1 = c1.getAccessor();
      BigIntVector.Accessor a2 = c2.getAccessor();
      
      for(int i =0; i < c1.getAccessor().getValueCount(); i++){
        recordCount++;
        assert previousInt <= a1.get(i);
        
        if(previousInt != a1.get(i)){
          previousLong = Long.MAX_VALUE;
          previousInt = a1.get(i);
        }
        
        assert previousLong >= a2.get(i);
        
        //System.out.println(previousInt + "\t" + a2.get(i));
        
      }
     
      
    }
    
    System.out.println(String.format("Sorted %,d records in %d batches.", recordCount, batchCount));

    if(context.getFailureCause() != null){
      throw context.getFailureCause();
    }
    assertTrue(!context.isFailed());
  }
  
  @AfterClass
  public static void tearDown() throws Exception{
    // pause to get logger to catch up.
    Thread.sleep(1000);
  }
}
