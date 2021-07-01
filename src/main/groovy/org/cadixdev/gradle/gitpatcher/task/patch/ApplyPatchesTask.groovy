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
import org.cadixdev.gradle.gitpatcher.task.UpdateSubmodulesTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ApplyPatchesTask extends PatchTask {

    @Internal
    UpdateSubmodulesTask updateTask

    @Override @Internal
    File getPatchDir() {
        return super.getPatchDir();
    }

    @Override @InputFiles
    File[] getPatches() {
        return super.getPatches()
    }

    @Override @OutputDirectory
    File getRepo() {
        return super.getRepo()
    }

    @Override @OutputFile
    File getRefCache() {
        return super.getRefCache()
    }

    {
        outputs.upToDateWhen {
            if (!repo.directory) {
                return false
            }

            def git = new Git(repo)
            return git.status.empty && cachedRef == git.ref && cachedSubmoduleRef == updateTask.ref
        }
    }

    @TaskAction
    void applyPatches() {
        def git = new Git(submoduleRoot)
        git.branch('-f', 'upstream') >> null

        def gitDir = new File(repo, '.git')
        if (!gitDir.isDirectory() || gitDir.list().length == 0) {
            logger.lifecycle 'Creating {} repository...', repo

            assert gitDir.deleteDir()
            git.repo = root
            git.clone('--recursive', submodule, repo.absolutePath, '-b', 'upstream') >> out
        }

        logger.lifecycle 'Resetting {}...', repo

        git.repo = repo
        git.fetch('origin') >> null
        git.checkout('-B', 'master', 'origin/upstream') >> null
        git.reset('--hard') >> out

        if (!patchDir.directory) {
            assert patchDir.mkdirs(), 'Failed to create patch directory'
        }

        if ('true'.equalsIgnoreCase(git.config('commit.gpgsign').readText())) {
            logger.warn("Disabling GPG signing for the gitpatcher repository")
            git.config('commit.gpgsign', 'false') >> out
        }

        def patches = this.patches
        if (patches.length > 0) {
            logger.lifecycle 'Applying patches from {} to {}', patchDir, repo

            git.am('--abort') >>> null
            git.am('--3way', *patches.collect { it.absolutePath }) >> out

            logger.lifecycle 'Successfully applied patches from {} to {}', patchDir, repo
        }

        refCache.text = git.ref + '\n' + updateTask.ref
    }

}
