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

import static net.minecrell.gitpatcher.git.Patcher.log
import static net.minecrell.gitpatcher.git.Patcher.openGit

import net.minecrell.gitpatcher.git.MailPatch
import net.minecrell.gitpatcher.git.Patcher
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.patch.Patch
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class MakePatchesTask extends PatchTask {

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
        File[] patches
        if (patchDir.isDirectory()) {
            patches = this.patches
        } else {
            assert patchDir.mkdirs(), 'Failed to create patch directory'
            patches = null
        }

        openGit(repo) {
            didWork = false

            def i = 0
            for (def commit : log(it, 'origin/upstream')) {
                def out = new File(patchDir, Patcher.suggestFileName(commit, i+1))

                if (patches != null && i < patches.length) {
                    def current = patches[i]
                    if (out == current) {
                        // The patches are possibly the same - continue checking
                        def bytes = current.bytes
                        def header = MailPatch.parseHeader(bytes)

                        if (header.represents(commit)) {
                            def diff = new ByteArrayOutputStream(current.bytes.length)
                            def formatter = new DiffFormatter(diff)
                            formatter.repository = repository

                            try {
                                // The commit header is the same, now check the diff
                                def patch = new Patch()
                                patch.parse(bytes, 0, bytes.length)

                                formatter.format(patch.files)
                                def currentDiff = diff.toByteArray()

                                diff.reset()
                                Patcher.formatPatch(formatter, commit)
                                def newDiff = diff.toByteArray()

                                if (currentDiff == newDiff) {
                                    // The patches are identical, we can skip this
                                    logger.lifecycle 'Skipping patch: {} (up-to-date)', current.name
                                    i++
                                    continue
                                }
                            } finally {
                                formatter.release()
                            }

                        }
                    } else {
                        // Delete the old patch
                        assert current.delete(), 'Failed to delete patch'
                    }
                }


                logger.lifecycle 'Generating patch: {}', out.name
                didWork = true

                out.createNewFile()
                out.withOutputStream {
                    MailPatch.writePatch(repository, commit, it)
                }

                i++
            }

            // Delete patches that don't exist anymore
            if (patches != null && i < patches.length) {
                for (; i < patches.length; i++) {
                    assert patches[i].delete(), 'Failed to delete patch'
                }
            }
        }
    }

}
