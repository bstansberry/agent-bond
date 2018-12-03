/*
 Copyright 2018, Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package io.fabric8.bond.jmx_exporter;

import java.lang.instrument.Instrumentation;

import io.prometheus.jmx.shaded.io.prometheus.jmx.JavaAgent;

/**
 * Wrapper around the jmx-exporter JavaAgent class that asynchronously invokes its premain
 * after first waiting for the JBoss Logmanager LogManager implementation to initialize.
 *
 * @author Brian Stansberry
 */
public class JavaAgentWrapper {

    public JavaAgentWrapper() {

    }

    public static void premain(final String agentArgument, final Instrumentation instrumentation) {
        // start the JMX Exporter in a new daemon thread
        Thread jolokiaStartThread = new Thread("JMXExporterStart") {
            public void run() {
                try {
                    // block until the server supporting early detection is initialized
                    awaitServerInitialization(instrumentation);
                    JavaAgent.premain(agentArgument, instrumentation);
                } catch (Exception exp) {
                    System.err.println("Could not start jmx-exporter agent: " + exp);
                }
            }
        };
        jolokiaStartThread.setDaemon(true);
        jolokiaStartThread.start();
    }

    private static void awaitServerInitialization(Instrumentation instrumentation) {
        int count = 0;

        while(count * 200 < 300000) {
            String loggingManagerClassName = System.getProperty("java.util.logging.manager");
            if (loggingManagerClassName != null && isClassLoaded(loggingManagerClassName, instrumentation)) {
                return;
            }

            try {
                Thread.sleep(200L);
                ++count;
            } catch (InterruptedException var5) {
                throw new RuntimeException(var5);
            }
        }

        throw new IllegalStateException(String.format("Detected JBoss Module loader, but property java.util.logging.manager is not set after %d seconds", 300));
    }

    private static boolean isClassLoaded(String className, Instrumentation instrumentation) {
        if (instrumentation != null && className != null) {
            Class<?>[] classes = instrumentation.getAllLoadedClasses();
            Class[] var4 = classes;
            int var5 = classes.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                Class<?> c = var4[var6];
                if (className.equals(c.getName())) {
                    return true;
                }
            }

            return false;
        } else {
            throw new IllegalArgumentException("instrumentation and className must not be null");
        }
    }
}
