/*
 * Copyright (c) 2015, Minecrell <https://github.com/Minecrell>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.cadixdev.gradle.gitpatcher

import groovy.transform.CompileStatic

class Git {

    File repo

    Git(File repo) {
        setRepo(repo)
    }

    void setRepo(File repo) {
        this.repo = repo
        assert repo.exists()
    }

    String getStatus() {
        return run('status', ['-z']).text
    }

    String getRef() {
        return rev_parse('HEAD').text.readLines().first().trim()
    }

    Command run(String name, Object input) {
        return new Command(['git', '--no-pager', name.replace('_' as char, '-' as char), *input].execute(null as String[], repo))
    }

    @Override
    Command invokeMethod(String name, Object input) {
        return run(name, input)
    }

    @CompileStatic
    static class Command {

        final Process process

        private Command(Process process) {
            this.process = process
        }

        int run() {
            def result = process.waitFor()
            return result
        }

        void execute() {
            def result = run()
            assert result == 0, 'Process returned error code'
        }

        void writeTo(OutputStream out) {
            process.consumeProcessOutput(out, System.err)
            execute()
        }

        void forceWriteTo(OutputStream out) {
            process.consumeProcessOutput(out, out)
            run()
        }

        def rightShift = this.&writeTo
        def rightShiftUnsigned = this.&forceWriteTo

        String getText() {
            process.consumeProcessErrorStream((OutputStream) System.err)
            def text = process.inputStream.text.trim()
            execute()
            return text
        }

        String readText() {
            process.consumeProcessErrorStream((OutputStream) System.err)
            def text = process.inputStream.text.trim()
            return run() == 0 ? text : null
        }

    }

}
