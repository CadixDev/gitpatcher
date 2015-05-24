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

import static java.lang.Boolean.parseBoolean
import static java.lang.System.getProperty
import static net.minecrell.gitpatcher.git.Patcher.withGit
import static org.eclipse.jgit.api.Git.wrap
import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD
import static org.eclipse.jgit.submodule.SubmoduleWalk.getSubmoduleRepository

import net.minecrell.gitpatcher.git.MailPatch
import org.eclipse.jgit.api.Git
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ApplyPatchesTask extends PatchTask {

    private static final boolean JGIT_APPLY = parseBoolean(getProperty("jgit.apply", "true"))

    @Override @InputFiles
    File[] getPatches() {
        return super.getPatches()
    }

    @Override @OutputDirectory
    File getRepo() {
        return super.getRepo()
    }

    @Override @OutputFile
    File getIndexFile() {
        return super.getIndexFile()
    }

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

            if (repository.getRef('master') == null) {
                // Create the master branch
                branchCreate().setName('master').call()
            }

            checkout().setName('master').call()
            reset().setMode(HARD).setRef('origin/upstream').call()
            clean().setCleanDirectories(true).call()

            if (patchDir.isDirectory()) {
                logger.lifecycle 'Applying patches from {} to {}', patchDir, repo

                if (!JGIT_APPLY) {
                    ['git', 'am', '--abort'].execute(null as String[], repo).waitFor()
                }

                for (def file : patches) {
                    if (JGIT_APPLY) {
                        logger.lifecycle 'Applying: {}', file.name

                        def data = new ByteArrayInputStream(file.bytes)
                        def patch = MailPatch.parseHeader(data)
                        data.reset()

                        apply().setPatch(data).call()
                        commit().setAuthor(patch.author)
                                .setMessage(patch.message)
                                .setAll(true)
                                .call()
                    } else {
                        def p = ['git', 'am', '--3way', file.absolutePath].execute(null as String[], repo)
                        p.consumeProcessOutput(System.out as OutputStream, System.err)
                        def r = p.waitFor()
                        assert r == 0, "Process returned error code $r"
                    }
                }

                logger.lifecycle 'Successfully applied patches from {} to {}', patchDir, repo
            }
        }
    }

}
