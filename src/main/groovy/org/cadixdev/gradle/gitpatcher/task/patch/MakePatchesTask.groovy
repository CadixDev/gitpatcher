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

package org.cadixdev.gradle.gitpatcher.task.patch

import static java.lang.System.out

import org.cadixdev.gradle.gitpatcher.Git
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class MakePatchesTask extends PatchTask {

    private static final Closure HUNK = { it.startsWith('@@') }

    @Input
    String[] formatPatchArgs

    @Override @InputDirectory
    File getRepo() {
        return super.getRepo()
    }

    @Override @Internal
    File getRefCache() { // not used in this task
        return super.getRefCache()
    }

    @Override @OutputDirectory
    File getPatchDir() {
        return super.getPatchDir()
    }

    @Override @Internal
    File[] getPatches() {
        return super.getPatches()
    }


    {
        outputs.upToDateWhen {
            if (!repo.directory) {
                return false
            }

            def git = new Git(repo)
            return cachedRef == git.ref
        }
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
        git.format_patch(*formatPatchArgs, '-o', patchDir.absolutePath, 'origin/upstream') >> null

        git.repo = root
        git.add('-A', patchDir.absolutePath) >> out

        didWork = false
        for (def patch : patches) {
            List<String> diff = git.diff('--no-color', '-U1', '--staged', patch.absolutePath).text.readLines()
            if (isUpToDate(diff)) {
                logger.lifecycle 'Skipping {} (up-to-date)', patch.name
                git.reset('HEAD', patch.absolutePath) >> null
                git.checkout('--', patch.absolutePath) >> null
            } else {
                didWork = true
                logger.lifecycle 'Generating {}', patch.name
            }
        }
    }

    private static boolean isUpToDate(List<String> diff) {
        if (diff.empty) {
            return true
        }

        if (diff.contains('--- /dev/null')) {
            return false
        }

        // Check if there are max. 2 diff hunks (once for the hash, and once for the Git version)
        def count = diff.count(HUNK)
        if (count == 0) {
            return true
        }

        if (count > 2) {
            return false
        }

        for (def i = 0; i < diff.size(); i++) {
            if (HUNK(diff[i])) {
                def change = diff[i + 1]
                if (!change.startsWith('From', 1) && !change.startsWith('--', 1)) {
                    return false
                }
            }
        }

        return true
    }

}
