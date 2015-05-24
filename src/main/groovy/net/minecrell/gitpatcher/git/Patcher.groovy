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
package net.minecrell.gitpatcher.git

import static org.eclipse.jgit.revwalk.RevSort.REVERSE
import static org.eclipse.jgit.revwalk.RevSort.TOPO

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.SimpleType
import org.apache.commons.lang.StringUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

final class Patcher {

    private Patcher() {
    }

    static Git openGit(File repo, @ClosureParams(value = SimpleType, options = "org.eclipse.jgit.api.Git") @DelegatesTo(Git) Closure action) {
        withGit(Git.open(repo), action)
    }

    static Git withGit(Git git, @ClosureParams(FirstParam) @DelegatesTo(Git) Closure action) {
        action.delegate = git

        try {
            action.call(git)
            return git
        } finally {
            git.close()
        }
    }

    static RevWalk log(Git git, String start) {
        RevWalk walk = git.log().not(git.repository.resolve(start)).call()
        walk.sort(TOPO)
        walk.sort(REVERSE, true)
        return walk
    }

    static File[] findPatches(File patchDir) {
        return patchDir.listFiles({ dir, name ->
            name.endsWith('.patch') && StringUtils.isNumeric(name.substring(0, 4))
        } as FilenameFilter).sort()
    }

    static String suggestFileName(RevCommit commit, int num) {
        def result = new StringBuilder(String.format("%04d-", num))
        for (char c : commit.shortMessage.chars) {
            if (Character.isLetter(c) || Character.isDigit(c)) {
                result << c
            } else if (Character.isWhitespace(c) || c == '/' as char) {
                result << '-' as char
            }
        }

        result << '.patch'
        return result.toString()
    }

    static void formatPatch(DiffFormatter formatter, RevCommit commit) {
        formatter.format(commit.getParent(0).tree, commit.tree)
    }

}
