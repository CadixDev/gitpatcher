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

import com.google.common.collect.ImmutableList
import net.minecrell.gitpatcher.task.SubmoduleTask

abstract class PatchTask extends SubmoduleTask {

    File root

    File patchDir

    protected File[] getPatches() {
        if (!patchDir.directory) {
            return []
        }

        return patchDir.listFiles({ dir, name -> name.endsWith('.patch') } as FilenameFilter).sort()
    }

    File getSubmoduleRoot() {
        return new File(root, submodule)
    }

    File getGitDir() {
        return new File(repo, '.git')
    }

    File getRefCache() {
        return new File(gitDir, '.gitpatcher_ref')
    }

    private List<String> cachedRefs

    private void readCache() {
        if (cachedRefs == null) {
            File refCache = this.refCache
            if (refCache.file) {
                this.cachedRefs = ImmutableList.copyOf refCache.readLines().findResults {
                    def trimmed = it.trim()
                    !trimmed.empty && !trimmed.startsWith('#') ? trimmed : null
                }
            } else {
                this.cachedRefs = ImmutableList.of()
            }
        }
    }

    String getCachedRef() {
        readCache()
        return cachedRefs[0]
    }

    String getCachedSubmoduleRef() {
        readCache()
        return cachedRefs[1]
    }

}
