/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 * @author oleg@klyudt.me (Oleg Klyudt)
 */

package me.klyudt.benchmarks.locks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmarks performance of two implementations of a shared lock.
 *
 * <p>Shared Lock has conceptually two locks: A and B.
 * <ul>
 *  <li>Whenever lock A is being held by at least one thread, no other thread can hold B lock.
 *  <li>Whenever lock B is being held by at least one thread, no other thread can hold B lock.
 *  <li>Attempt to acquire lock A may only succeed if lock B is not being held by any thread.
 *  <li>Attempt to acquire lock B may only succeed if lock A is not being held by any thread.
 * </ul>
 *
 * <p>This class contains two implementations of Shared lock:
 * <ol>
 *  <li>SpinLock based on CAS
 *  <li>SynchronizedLock based on intrinsic locks
 * </ol>
 *
 * <p>Benchmarking results:
 * <br># 8 threads
 * <br>Benchmark                          Mode  Samples     Score  Score error  Units
 * <br>spinLock_lockUnlockA               avgt      100  1405.013       40.093  ns/op
 * <br>spinLock_lockUnlockALockUnlockB    avgt      100  2887.881       33.544  ns/op
 * <br>spinLock_lockUnlockB               avgt      100  1514.518       39.093  ns/op
 * <br>spinLock_lockUnlockBLockUnlockA    avgt      100  2879.916       24.103  ns/op
 * <br>syncLock_lockUnlockA               avgt      100  1451.663       10.291  ns/op
 * <br>syncLock_lockUnlockALockUnlockB    avgt      100  2364.621       59.594  ns/op
 * <br>syncLock_lockUnlockB               avgt      100  1451.079        9.760  ns/op
 * <br>syncLock_lockUnlockBLockUnlockA    avgt      100  2554.617       54.299  ns/op
 * <br># 40 threads
 * <br>Benchmark                          Mode  Samples       Score  Score error  Units
 * <br>spinLock_lockUnlockA               avgt       50    7842.947      116.478  ns/op
 * <br>spinLock_lockUnlockALockUnlockB    avgt       50  146582.986   150250.277  ns/op
 * <br>spinLock_lockUnlockB               avgt       50    6927.919      465.643  ns/op
 * <br>spinLock_lockUnlockBLockUnlockA    avgt       50  394253.295   683460.013  ns/op
 * <br>syncLock_lockUnlockA               avgt       50    7887.058      115.669  ns/op
 * <br>syncLock_lockUnlockALockUnlockB    avgt       50   13869.726      277.180  ns/op
 * <br>syncLock_lockUnlockB               avgt       50    7653.966       43.491  ns/op
 * <br>syncLock_lockUnlockBLockUnlockA    avgt       50   13612.982      161.783  ns/op
 *
 * <p>For JMH benchmark framework usage examples see:
 * http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/
 *
 * <p>For installation instructions see:
 * http://openjdk.java.net/projects/code-tools/jmh/
 **/
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SharedLocksBenchmark {
    @State(Scope.Benchmark)
    public static class SpinLock {
        // lockCounter > 0 for num of A locks held
        // lockCounter < 0 for num of B locks held
        // lockCounter == 0 for no locks held
        private final AtomicInteger lockCounter = new AtomicInteger(0);

        void lockA() {
            int curLockCnt;
            while (true) {
                while(this.lockCounter.get() < 0) {}
                // curLockCnt >= 0
                curLockCnt = this.lockCounter.get();
                if (curLockCnt < 0) continue;
                if (lockCounter.compareAndSet(curLockCnt, curLockCnt + 1)) break;
            }
        }

        void unlockA() {
            lockCounter.decrementAndGet();
        }

        void lockB() {
            int curLockCnt;
            while (true) {
                while(this.lockCounter.get() > 0) {}
                // curLockCnt <= 0
                curLockCnt = this.lockCounter.get();
                if (curLockCnt > 0) continue;
                if (lockCounter.compareAndSet(curLockCnt, curLockCnt - 1)) break;
            }
        }

        void unlockB() {
            lockCounter.incrementAndGet();
        }
    }

    @State(Scope.Benchmark)
    public static class SynchronizedLock {
        private int cntr;

        synchronized void lockA() throws InterruptedException {
            while (cntr < 0) {
                wait();
            }
            cntr++;
        }

        synchronized void unlockA() {
            cntr--;
            if (cntr == 0) {
                notifyAll();
            }
        }

        synchronized void lockB() throws InterruptedException {
            while (cntr > 0) {
                wait();
            }
            cntr--;
        }

        synchronized void unlockB() {
            cntr++;
            if (cntr == 0) {
                notifyAll();
            }
        }
    }

    int dummy;

    @Setup
    public void initDummy() {
        dummy = 0;
    }

    @Benchmark
    public int spinLock_lockUnlockA(SpinLock lock) {
        lock.lockA();
        dummy++;
        lock.unlockA();
        return dummy;
    }

    @Benchmark
    public int spinLock_lockUnlockB(SpinLock lock) {
        lock.lockB();
        dummy--;
        lock.unlockB();
        return dummy;
    }

    @Benchmark
    public int spinLock_lockUnlockALockUnlockB(SpinLock lock) {
        lock.lockA();
        dummy++;
        lock.unlockA();
        lock.lockB();
        dummy++;
        lock.unlockB();
        return dummy;
    }

    @Benchmark
    public int spinLock_lockUnlockBLockUnlockA(SpinLock lock) {
        lock.lockB();
        dummy--;
        lock.unlockB();
        lock.lockA();
        dummy++;
        lock.unlockA();
        return dummy;
    }

    @Benchmark
    public int syncLock_lockUnlockA(SynchronizedLock lock) throws InterruptedException {
        lock.lockA();
        dummy++;
        lock.unlockA();
        return dummy;
    }

    @Benchmark
    public int syncLock_lockUnlockB(SynchronizedLock lock) throws InterruptedException {
        lock.lockB();
        dummy--;
        lock.unlockB();
        return dummy;
    }

    @Benchmark
    public int syncLock_lockUnlockALockUnlockB(SynchronizedLock lock) throws InterruptedException {
        lock.lockA();
        dummy++;
        lock.unlockA();
        lock.lockB();
        dummy++;
        lock.unlockB();
        return dummy;
    }

    @Benchmark
    public int syncLock_lockUnlockBLockUnlockA(SynchronizedLock lock) throws InterruptedException {
        lock.lockB();
        dummy--;
        lock.unlockB();
        lock.lockA();
        dummy--;
        lock.unlockA();
        return dummy;
    }
    /*
     * ============================== HOW TO RUN THIS TEST: ====================================
     *
     * You are expected to see the drastic difference in shared and unshared cases,
     * because you either contend for single memory location, or not. This effect
     * is more articulated on large machines.
     *
     * You can run this test:
     *
     * a) Via the command line:
     *    $ mvn clean install
     *    $ java -jar target/benchmarks.jar SharedLocksBenchmark -wi 5 -i 50 -t 8 -f 2
     *    (we requested 5 measurement/warmup iterations, with 4 threads, single fork)
     *
     * b) Via the Java API:
     *    (see the JMH homepage for possible caveats when running from IDE:
     *      http://openjdk.java.net/projects/code-tools/jmh/)
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SharedLocksBenchmark.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(50)
                .threads(8)
                .forks(2)
                .build();

        new Runner(opt).run();
    }
}
