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
package net.minecrell.gitpatcher.task.patch

import static java.lang.System.out

import net.minecrell.gitpatcher.Git
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class MakePatchesTask extends PatchTask {

    private static final Closure HUNK = { it.startsWith('@@') }

    @Override @InputDirectory
    File getRepo() {
        return super.getRepo()
    }

    @Override @InputFile
    File getIndexFile() {
        return super.getIndexFile()
    }

    @Override @OutputDirectory
    File getPatchDir() {
        return super.getPatchDir()
    }

    @TaskAction
    void makePatches() {
        if (patchDir.isDirectory()) {
            def patches = this.patches
            if (patches) {
                assert patches*.delete(), 'Failed to delete old patch'
            }
        } else {
            assert patchDir.mkdirs(), 'Failed to create patch directory'
        }

        def git = new Git(repo)
        git.format_patch('--no-stat', '-N', '-o', patchDir.absolutePath, 'origin/upstream') >> null

        git.repo = root
        git.add('-A', patchDir.absolutePath) >> out

        didWork = false
        for (def patch : patches) {
            def diff = (git.diff('--no-color', '-U1', '--staged', patch.absolutePath) as String).readLines()
            def first = diff.findIndexOf(HUNK)
            if (first >= 0 && diff[first + 1].startsWith('From', 1)) {
                def last = diff.findLastIndexOf(HUNK)
                if (last >= 0 && diff[last + 1].startsWith('--', 1)) {
                    if (!diff.subList(first + 4, last).find(HUNK)) {
                        logger.lifecycle 'Skipping {} (up-to-date)', patch.name
                        git.reset('HEAD', patch.absolutePath) >> null
                        git.checkout('--', patch.absolutePath) >> null
                        continue
                    }
                }
            }

            didWork = true
            logger.lifecycle 'Generating {}', patch.name
        }
    }

}
