package net.minecrell.gitpatcher.git

import static org.eclipse.jgit.revwalk.RevSort.REVERSE
import static org.eclipse.jgit.revwalk.RevSort.TOPO

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.SimpleType
import org.eclipse.jgit.api.Git
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

}
