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

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.apache.commons.lang.StringUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

@EqualsAndHashCode
@ToString(includePackage = false)
class MailPatch {

    private static final JGIT_VERSION = 'jgit/' + (Git.class.getPackage().getImplementationVersion() ?: 'unknown')

    private static final DateTimeFormatter dateFormat =
            DateTimeFormat.forPattern("EEE, d MMM yyyy HH:mm:ss Z").withLocale(Locale.US).withOffsetParsed()

    final PersonIdent author
    final String message

    MailPatch(PersonIdent author, String message) {
        this.author = author
        this.message = message
    }

    boolean represents(RevCommit commit) {
        return author == commit.authorIdent && message == commit.fullMessage
    }

    static MailPatch parseHeader(byte[] data) {
        return parseHeader(new ByteArrayInputStream(data))
    }

    static MailPatch parseHeader(InputStream is) {
        return parseHeader(is.newReader('UTF-8'))
    }

    static MailPatch parseHeader(BufferedReader reader) {
        String from = null
        String time = null
        StringBuilder builder = null

        String line
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue
            }

            line = line.trim()

            if (line.startsWith('diff')) {
                break
            }

            if (builder == null) {
                int pos = line.indexOf(':')
                if (pos == -1) {
                    continue
                }

                def header = line.substring(0, pos)
                def content = line.substring(pos + 2)
                switch (header) {
                    case "From":
                        from = content
                        break
                    case "Date":
                        time = content
                        break
                    case "Subject":
                        builder = new StringBuilder(StringUtils.removeStart(content, '[PATCH] '))
                }
            } else {
                if (!line.empty) {
                    builder << '\n' << line
                }
            }
        }

        assert from != null, 'Missing commit author'
        assert time != null, 'Missing commit date'
        assert builder != null, 'Missing commit message'

        def author = parseAuthor(from)
        def date = dateFormat.parseDateTime(time)
        def message = builder.toString()

        return new MailPatch(new PersonIdent(author[0], author[1], date.toDate(), date.getZone().toTimeZone()), message)
    }

    private static String[] parseAuthor(String author) {
        int start = author.lastIndexOf('<')
        assert start != -1, 'Incomplete commit author'

        def name = unquote(author.substring(0, start - 1))

        def end = author.lastIndexOf('>')
        assert start != -1, 'Invalid commit author'


        return [name, author.substring(start + 1, end)]
    }

    private static String unquote(String s) {
        if (s.length() > 2) {
            if (s.charAt(0) == '"' as char && s.charAt(s.length() - 1) == '"' as char) {
                return s.substring(1, s.length() - 1)
            }
        }

        return s
    }

    static void writeHeader(RevCommit commit, Writer writer) {
        writer << 'From ' << commit.id.name << ' Mon Sep 17 00:00:00 2001\n'
        writer << 'From: ' << commit.authorIdent.name << ' <' << commit.authorIdent.emailAddress << '>\n'

        writer << 'Date: '
        dateFormat.printTo writer, new DateTime(commit.authorIdent.getWhen(), DateTimeZone.forTimeZone(commit.authorIdent.timeZone))
        writer << '\n'

        def message = commit.fullMessage
        writer << 'Subject: [PATCH] ' << message << (message.endsWith('\n') ? '\n\n' : '\n\n\n')
        writer.flush()
    }

    static void writePatch(Repository repo, RevCommit commit, OutputStream out) {
        def writer = out.newWriter('UTF-8')
        writeHeader(commit, writer)

        def formatter = new DiffFormatter(out)
        formatter.repository = repo
        try {
            Patcher.formatPatch(formatter, commit)
        } finally {
            formatter.release()
        }

        writer << '-- \n' << JGIT_VERSION << '\n'
        writer.flush()
    }

}
