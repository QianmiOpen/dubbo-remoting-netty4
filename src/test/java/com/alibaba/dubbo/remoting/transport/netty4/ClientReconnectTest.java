/*
 * Copyright 1999-2011 Alibaba Group.
 *  
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
 */
package com.alibaba.dubbo.remoting.transport.netty4;

import org.apache.log4j.Level;
import org.junit.Assert;
import org.junit.Test;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.utils.DubboAppender;
import com.alibaba.dubbo.common.utils.LogUtil;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.Client;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.Server;
import com.alibaba.dubbo.remoting.exchange.Exchangers;
import com.alibaba.dubbo.remoting.exchange.support.ExchangeHandlerAdapter;

/**
 * 客户端重连测试
 * @author chao.liuc
 *
 */
public class ClientReconnectTest {

    @Test
    public void testCase()throws RemotingException, InterruptedException {
        testReconnect();
        testClientReconnectMethod();
        testReconnectWaringLog();
    }

    public void testReconnect() throws RemotingException, InterruptedException{
        {
            int port = NetUtils.getAvailablePort();
            Client client = startClient(port, 500);
            Assert.assertEquals(false, client.isConnected());
            Server server = startServer(port);
            for (int i = 0; i < 3 && ! client.isConnected(); i++) {
                Thread.sleep(500);
            }
            Assert.assertEquals(true, client.isConnected());
            client.close(1500);
            server.close(1500);
        }
        {
            int port = NetUtils.getAvailablePort();
            Client client = startClient(port, 500);
            Assert.assertEquals(false, client.isConnected());
            Server server = startServer(port);
            for(int i=0;i<2;i++){
                Thread.sleep(100);
            }
            Assert.assertEquals(false, client.isConnected());
            client.close(2000);
            server.close(2000);
        }
    }
    
    /**
     * 测试client重连方法不会导致重连线程失效.
     */
    public void testClientReconnectMethod() throws RemotingException, InterruptedException{
        int port = NetUtils.getAvailablePort();
        String url = "exchange://127.0.0.3:"+port + "/client.reconnect.test?transporter=netty4&check=false&"
        +Constants.RECONNECT_KEY+"="+10 //1ms reconnect,保证有足够频率的重连
        +"&reconnect.waring.period=100";
        DubboAppender.doStart();
        Client client = Exchangers.connect(url);
        try {
			client.reconnect();
		} catch (Exception e) {
			//do nothing
		}
        Thread.sleep(1500);//重连线程的运行
        Assert.assertTrue("have more then one warn msgs . bug was :" + LogUtil.findMessage(Level.WARN, "client reconnect to "),LogUtil.findMessage(Level.WARN, "client reconnect to ") >1);
        DubboAppender.doStop();
    }
    public static void main(String[] args) {
		System.out.println(3%1);
	}
    
    /**
     * 重连日志的校验
     */
    public void testReconnectWaringLog() throws RemotingException, InterruptedException{
        int port = NetUtils.getAvailablePort();
        DubboAppender.doStart();
        String url = "exchange://127.0.0.4:"+port + "/client.reconnect.test?transporter=netty4&check=false&"
        +Constants.RECONNECT_KEY+"="+100
        +"&"+Constants.SHUTDOWN_TIMEOUT_KEY+ "=1"//shutdown时间足够短，确保error日志输出
        +"&reconnect.waring.period=500";//每隔多少warning记录一次
        try{
            Exchangers.connect(url);
        }catch (Exception e) {
            //do nothing
        }
        int count =  0;
        for (int i=0;i<10;i++){
            count =  LogUtil.findMessage(Level.WARN, "client reconnect to ") ; 
            if (count >=1){
                break;
            }
            Thread.sleep(50);//重连线程的运行
        }
        Assert.assertTrue("warning message count must >= 1, real :"+count, count>= 1);
        DubboAppender.doStop();
    }
    
    public Client startClient(int port , int reconnectPeriod) throws RemotingException{
        final String url = "exchange://127.0.0.1:"+port + "/client.reconnect.test?check=false&"+Constants.RECONNECT_KEY+"="+reconnectPeriod +"&transporter=netty4&reconnect.waring.period=3000";;
        return Exchangers.connect(url);
    }
    
    public Server startServer(int port) throws RemotingException{
        final String url = "exchange://127.0.0.1:"+port +"/client.reconnect.test?transporter=netty4&reconnect.waring.period=3000";
        return Exchangers.bind(url, new HandlerAdapter());
    }
    
    static class HandlerAdapter extends ExchangeHandlerAdapter{
        public void connected(Channel channel) throws RemotingException {
        }
        public void disconnected(Channel channel) throws RemotingException {
        }
        public void caught(Channel channel, Throwable exception) throws RemotingException {
        }
    }
}