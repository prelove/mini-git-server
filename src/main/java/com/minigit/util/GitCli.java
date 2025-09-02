package com.minigit.util;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GitCli {
    private static void usage() {
        System.out.println("Mini Git CLI (JGit) - basic commands");
        System.out.println("Usage:");
        System.out.println("  java -cp <jar> com.minigit.client.GitCli <command> [args...]");
        System.out.println("Commands (examples):");
        System.out.println("  init <path>");
        System.out.println("  clone <remoteUrl> <localPath>");
        System.out.println("  status <repoPath>");
        System.out.println("  add <repoPath> <path1> [path2 ...]");
        System.out.println("  commit <repoPath> -m \"message\"");
        System.out.println("  log <repoPath> [--max N]");
        System.out.println("  branch <repoPath> (lists branches)");
        System.out.println("  branch -c <repoPath> <newBranch>");
        System.out.println("  checkout <repoPath> <branchOrCommit>");
        System.out.println("  push <repoPath> <remote> <ref>  (env GIT_USER/GIT_PASSWORD optional)");
        System.out.println("  pull <repoPath> <remote> <ref>  (env GIT_USER/GIT_PASSWORD optional)");
        System.out.println("  remote -v <repoPath>");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            return;
        }
        String cmd = args[0];
        try {
            switch (cmd) {
                case "init":
                    cmdInit(args);
                    break;
                case "clone":
                    cmdClone(args);
                    break;
                case "status":
                    cmdStatus(args);
                    break;
                case "add":
                    cmdAdd(args);
                    break;
                case "commit":
                    cmdCommit(args);
                    break;
                case "log":
                    cmdLog(args);
                    break;
                case "branch":
                    cmdBranch(args);
                    break;
                case "checkout":
                    cmdCheckout(args);
                    break;
                case "push":
                    cmdPush(args);
                    break;
                case "pull":
                    cmdPull(args);
                    break;
                case "remote":
                    cmdRemote(args);
                    break;
                default:
                    System.err.println("Unknown command: " + cmd);
                    usage();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static void cmdInit(String[] args) throws IOException, GitAPIException {
        if (args.length < 2) {
            System.err.println("init <path>");
            return;
        }
        File dir = new File(args[1]);
        if (!dir.exists()) dir.mkdirs();
        try (Git git = Git.init().setDirectory(dir).call()) {
            System.out.println("Initialized empty Git repository in " + git.getRepository().getDirectory());
        }
    }

    private static void cmdClone(String[] args) throws GitAPIException {
        if (args.length < 3) {
            System.err.println("clone <remoteUrl> <localPath>");
            return;
        }
        String remote = args[1];
        String local = args[2];
        System.out.println("Cloning " + remote + " -> " + local);
        CloneCommand cc = Git.cloneRepository()
                .setURI(remote)
                .setDirectory(new File(local));
        CredentialsProvider cp = getEnvCredentials();
        if (cp != null) cc.setCredentialsProvider(cp);
        try (Git git = cc.call()) {
            System.out.println("Clone completed to " + git.getRepository().getDirectory());
        }
    }

    private static void cmdStatus(String[] args) throws IOException, GitAPIException {
        if (args.length < 2) {
            System.err.println("status <repoPath>");
            return;
        }
        try (Git git = openGit(args[1])) {
            Status status = git.status().call();
            System.out.println("Added: " + status.getAdded());
            System.out.println("Changed: " + status.getChanged());
            System.out.println("Removed: " + status.getRemoved());
            System.out.println("Modified: " + status.getModified());
            System.out.println("Missing: " + status.getMissing());
            System.out.println("Untracked: " + status.getUntracked());
            if (status.hasUncommittedChanges()) System.out.println("Repository has uncommitted changes.");
            else System.out.println("Clean.");
        }
    }

    private static void cmdAdd(String[] args) throws IOException, GitAPIException {
        if (args.length < 3) {
            System.err.println("add <repoPath> <path1> [path2 ...]");
            return;
        }
        String repoPath = args[1];
        String[] paths = Arrays.copyOfRange(args, 2, args.length);
        try (Git git = openGit(repoPath)) {
            AddCommand add = git.add();
            for (String p : paths) {
                add.addFilepattern(p);
            }
            add.call();
            System.out.println("Added: " + Arrays.toString(paths));
        }
    }

    private static void cmdCommit(String[] args) throws IOException, GitAPIException {
        if (args.length < 4) {
            System.err.println("commit <repoPath> -m \"message\"");
            return;
        }
        String repoPath = args[1];
        String message = null;
        for (int i = 2; i < args.length; i++) {
            if ("-m".equals(args[i]) && i + 1 < args.length) {
                message = args[i + 1];
                break;
            }
        }
        if (message == null) {
            System.err.println("commit requires -m \"message\"");
            return;
        }
        try (Git git = openGit(repoPath)) {
            RevCommit commit = git.commit().setMessage(message).call();
            System.out.println("Committed: " + commit.getId().getName());
        }
    }

    private static void cmdLog(String[] args) throws IOException, GitAPIException {
        if (args.length < 2) {
            System.err.println("log <repoPath> [--max N]");
            return;
        }
        String repoPath = args[1];
        int max = 50;
        for (int i = 2; i < args.length; i++) {
            if ("--max".equals(args[i]) && i + 1 < args.length) {
                try { max = Integer.parseInt(args[i + 1]); } catch (NumberFormatException ignored) {}
            }
        }
        try (Git git = openGit(repoPath)) {
            Iterable<RevCommit> logs = git.log().setMaxCount(max).call();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (RevCommit c : logs) {
                PersonIdent author = c.getAuthorIdent();
                System.out.println("commit " + c.getId().getName().substring(0, 10));
                System.out.println("Author: " + author.getName() + " <" + author.getEmailAddress() + ">");
                System.out.println("Date:   " + sdf.format(author.getWhen()));
                System.out.println();
                System.out.println("    " + c.getFullMessage().replace("\n", "\n    "));
                System.out.println();
            }
        }
    }

    private static void cmdBranch(String[] args) throws IOException, GitAPIException {
        if (args.length < 2) {
            System.err.println("branch <repoPath> or branch -c <repoPath> <newBranch>");
            return;
        }
        if ("-c".equals(args[1]) && args.length >= 4) {
            String repoPath = args[2];
            String newBranch = args[3];
            try (Git git = openGit(repoPath)) {
                git.branchCreate().setName(newBranch).call();
                System.out.println("Created branch " + newBranch);
            }
            return;
        }
        String repoPath = args[1];
        try (Git git = openGit(repoPath)) {
            ListBranchCommand lb = git.branchList();
            List<Ref> list = lb.call();
            for (Ref r : list) {
                System.out.println(r.getName());
            }
        }
    }

    private static void cmdCheckout(String[] args) throws IOException, GitAPIException {
        if (args.length < 3) {
            System.err.println("checkout <repoPath> <branchOrCommit>");
            return;
        }
        String repoPath = args[1];
        String name = args[2];
        try (Git git = openGit(repoPath)) {
            CheckoutCommand co = git.checkout().setName(name);
            // If commit-ish (detached), JGit requires startPoint for new branch; attempt checkout directly
            co.call();
            System.out.println("Checked out " + name);
        }
    }

    private static void cmdPush(String[] args) throws IOException, GitAPIException {
        if (args.length < 4) {
            System.err.println("push <repoPath> <remote> <ref>");
            return;
        }
        String repoPath = args[1];
        String remote = args[2];
        String ref = args[3];
        CredentialsProvider cp = getEnvCredentials();
        try (Git git = openGit(repoPath)) {
            PushCommand push = git.push().setRemote(remote).setRefSpecs(new RefSpec(ref + ":" + ref));
            if (cp != null) push.setCredentialsProvider(cp);
            Iterable<PushResult> results = push.call();
            for (PushResult r : results) {
                System.out.println(r.getMessages());
            }
            System.out.println("Push done.");
        }
    }

    private static void cmdPull(String[] args) throws IOException, GitAPIException {
        if (args.length < 4) {
            System.err.println("pull <repoPath> <remote> <ref>");
            return;
        }
        String repoPath = args[1];
        String remote = args[2];
        String ref = args[3];
        CredentialsProvider cp = getEnvCredentials();
        try (Git git = openGit(repoPath)) {
            // set remote and credentials via fetch then merge
            FetchCommand fetch = git.fetch().setRemote(remote);
            if (cp != null) fetch.setCredentialsProvider(cp);
            fetch.call();
            // try merge from remote/ref (e.g. origin/master)
            String remoteRef = "refs/remotes/" + remote + "/" + ref;
            MergeResult m = git.merge()
                    .include(git.getRepository().exactRef(remoteRef))
                    .call();
            System.out.println("Merge status: " + m.getMergeStatus());
        }
    }

    private static void cmdRemote(String[] args) throws IOException, GitAPIException {
        if (args.length < 3) {
            System.err.println("remote -v <repoPath>");
            return;
        }
        if ("-v".equals(args[1])) {
            String repoPath = args[2];
            try (Git git = openGit(repoPath)) {
                RemoteListCommand r = git.remoteList();
                List<RemoteConfig> remotes = r.call();
                for (RemoteConfig rc : remotes) {
                    for (URIish u : rc.getURIs()) {
                        System.out.println(rc.getName() + "\t" + u.toString());
                    }
                }
            }
        } else {
            System.err.println("Unknown remote subcommand");
        }
    }

    private static Git openGit(String repoPath) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = builder.setGitDir(new File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
        return new Git(repo);
    }

    private static CredentialsProvider getEnvCredentials() {
        String u = System.getenv("GIT_USER");
        String p = System.getenv("GIT_PASSWORD");
        if (u != null && p != null) {
            return new UsernamePasswordCredentialsProvider(u, p);
        }
        return null;
    }
}
