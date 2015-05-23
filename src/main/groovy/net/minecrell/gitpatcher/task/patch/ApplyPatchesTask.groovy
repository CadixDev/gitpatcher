/*
 * PoreGradle
 * Copyright (c) 2015, Lapis <https://github.com/LapisBlue>
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

import static net.minecrell.gitpatcher.git.Patcher.withGit
import static org.eclipse.jgit.api.Git.wrap
import static org.eclipse.jgit.submodule.SubmoduleWalk.getSubmoduleRepository

import net.minecrell.gitpatcher.git.MailPatch
import org.eclipse.jgit.api.Git
import org.gradle.api.tasks.TaskAction

class ApplyPatchesTask extends PatchTask {

    @TaskAction
    void applyPatches() {
        File source = null
        withGit(wrap(getSubmoduleRepository(root, submodule))) {
            source = repository.directory
            branchCreate()
                    .setName('upstream')
                    .setForce(true)
                    .call()
        }

        Git git
        if (repo.isDirectory()) {
            git = Git.open(repo)
        } else {
            logger.lifecycle 'Creating {} repository...', repo

            git = Git.cloneRepository()
                    .setURI(source.toURI().toString())
                    .setDirectory(repo)
                    .call()
        }

        withGit(git) {
            logger.lifecycle 'Resetting {}...', repo

            fetch().setRemote('origin').call()
            branchCreate()
                    .setName('master')
                    .setForce(true)
                    .setStartPoint('origin/upstream')
                    .call()
            clean().setCleanDirectories(true).call()

            logger.lifecycle 'Applying patches from {} to {}', patchDir, repo

            for (def file : patchDir.listFiles({ dir, name -> name.endsWith('.patch')  } as FilenameFilter).sort()) {
                logger.lifecycle 'Applying: {}', file.name

                def data = new ByteArrayInputStream(file.bytes)
                def patch = MailPatch.parse(data)
                data.reset()

                apply().setPatch(data).call()
                commit().setAuthor(patch.author)
                        .setMessage(patch.message)
                        .setAll(true)
                        .call()
            }

            logger.lifecycle 'Successfully applied patches from {} to {}', patchDir, repo
        }
    }

}
