package com.minigit.util;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GitClientMain {

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }
        String cmd = args[0];
        try {
            switch (cmd) {
                case "init":     cmdInit(args); break;
                case "clone":    cmdClone(args); break;
                case "status":   cmdStatus(args); break;
                case "add":      cmdAdd(args); break;
                case "commit":   cmdCommit(args); break;
                case "log":      cmdLog(args); break;
                case "branch":   cmdBranch(args); break;
                case "checkout": cmdCheckout(args); break;
                case "push":     cmdPush(args); break;
                case "pull":     cmdPull(args); break;
                case "remote":   cmdRemote(args); break;
                case "help":     usage(); break;
                default:
                    System.err.println("Unknown command: " + cmd);
                    usage();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static void usage() {
        System.out.println("mgit (JGit client) commands:");
        System.out.println("  init <path>");
        System.out.println("  clone <remoteUrl> <localPath> [--user U --pass P]");
        System.out.println("  status <repoPath>");
        System.out.println("  add <repoPath> <path1> [path2 ...]");
        System.out.println("  commit <repoPath> -m \"message\"");
        System.out.println("  log <repoPath> [--max N]");
        System.out.println("  branch <repoPath>                # list");
        System.out.println("  branch -c <repoPath> <newBranch> # create");
        System.out.println("  checkout <repoPath> <branchOrCommit>");
        System.out.println("  push <repoPath> <remote> <ref> [--user U --pass P]");
        System.out.println("  pull <repoPath> <remote> <ref> [--user U --pass P]");
        System.out.println("  remote -v <repoPath>");
        System.out.println();
        System.out.println("Auth order: --user/--pass > env GIT_USER/GIT_PASSWORD > interactive");
    }

    // ---------------- commands ----------------

    private static void cmdInit(String[] args) throws GitAPIException {
        if (args.length < 2) { System.err.println("init <path>"); return; }
        File dir = new File(args[1]);
        if (!dir.exists()) dir.mkdirs();
        try (Git git = Git.init().setDirectory(dir).call()) {
            System.out.println("Initialized empty Git repository in " + git.getRepository().getDirectory());
        }
    }

    private static void cmdClone(String[] args) throws GitAPIException {
        if (args.length < 3) { System.err.println("clone <remoteUrl> <localPath> [--user U --pass P]"); return; }
        String remote = args[1];
        String local = args[2];
        String u = opt(args, "--user");
        String p = opt(args, "--pass");
        CredentialsProvider cp = creds(remote, u, p);
        CloneCommand cc = Git.cloneRepository().setURI(remote).setDirectory(new File(local));
        if (cp != null) cc.setCredentialsProvider(cp);
        try (Git git = cc.call()) {
            System.out.println("Cloned to " + git.getRepository().getDirectory());
        }
    }

    private static void cmdStatus(String[] args) throws IOException, GitAPIException {
        if (args.length < 2) { System.err.println("status <repoPath>"); return; }
        try (Git git = open(args[1])) {
            Status s = git.status().call();
            printStatus(s);
        }
    }

    private static void cmdAdd(String[] args) throws IOException, GitAPIException {
        if (args.length < 3) { System.err.println("add <repoPath> <path1> [path2 ...]"); return; }
        try (Git git = open(args[1])) {
            AddCommand add = git.add();
            for (int i = 2; i < args.length; i++) add.addFilepattern(args[i]);
            add.call();
            System.out.println("Added " + Arrays.toString(Arrays.copyOfRange(args, 2, args.length)));
        }
    }

    private static void cmdCommit(String[] args) throws IOException, GitAPIException {
        if (args.length < 4) { System.err.println("commit <repoPath> -m \"message\""); return; }
        String repo = args[1];
        String msg = opt(args, "-m");
        if (msg == null) { System.err.println("commit requires -m"); return; }
        try (Git git = open(repo)) {
            RevCommit c = git.commit().setMessage(msg).call();
            System.out.println("Committed " + c.getId().getName());
        }
    }

    private static void cmdLog(String[] args) throws IOException, GitAPIException {
        if (args.length < 2) { System.err.println("log <repoPath> [--max N]"); return; }
        String repo = args[1];
        int max = 50;
        String maxStr = opt(args, "--max");
        if (maxStr != null) try { max = Integer.parseInt(maxStr); } catch (NumberFormatException ignore) {}
        try (Git git = open(repo)) {
            Iterable<RevCommit> logs = git.log().setMaxCount(max).call();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (RevCommit c : logs) {
                PersonIdent a = c.getAuthorIdent();
                System.out.println("commit " + c.getId().getName().substring(0,10));
                System.out.println("Author: " + a.getName() + " <" + a.getEmailAddress() + ">");
                System.out.println("Date:   " + sdf.format(a.getWhen()));
                System.out.println();
                System.out.println("    " + c.getFullMessage().replace("\n", "\n    "));
                System.out.println();
            }
        }
    }

    private static void cmdBranch(String[] args) throws IOException, GitAPIException {
        if (args.length < 2) { System.err.println("branch <repoPath> or branch -c <repoPath> <newBranch>"); return; }
        if ("-c".equals(args[1]) && args.length >= 4) {
            String repo = args[2];
            String newBranch = args[3];
            try (Git git = open(repo)) {
                git.branchCreate().setName(newBranch).call();
                System.out.println("Created branch " + newBranch);
            }
            return;
        }
        String repo = args[1];
        try (Git git = open(repo)) {
            for (Ref r : git.branchList().call()) {
                String name = r.getName().replace("refs/heads/","");
                String current = git.getRepository().getBranch().equals(name) ? "*" : " ";
                System.out.println(current + " " + name);
            }
        }
    }

    private static void cmdCheckout(String[] args) throws IOException, GitAPIException {
        if (args.length < 3) { System.err.println("checkout <repoPath> <branchOrCommit>"); return; }
        String repo = args[1];
        String name = args[2];
        try (Git git = open(repo)) {
            git.checkout().setName(name).call();
            System.out.println("Checked out " + name);
        }
    }

    private static void cmdPush(String[] args) throws IOException, GitAPIException {
        if (args.length < 4) { System.err.println("push <repoPath> <remote> <ref> [--user U --pass P]"); return; }
        String repo = args[1], remote = args[2], ref = args[3];
        String u = opt(args, "--user");
        String p = opt(args, "--pass");
        try (Git git = open(repo)) {
            String url = git.getRepository().getConfig().getString("remote", remote, "url");
            CredentialsProvider cp = creds(url, u, p);
            PushCommand push = git.push().setRemote(remote).setRefSpecs(new RefSpec(ref + ":" + ref));
            if (cp != null) push.setCredentialsProvider(cp);
            Iterable<PushResult> results = push.call();
            for (PushResult r : results) System.out.print(r.getMessages());
            System.out.println("Push done.");
        }
    }

    private static void cmdPull(String[] args) throws IOException, GitAPIException {
        if (args.length < 4) { System.err.println("pull <repoPath> <remote> <ref> [--user U --pass P]"); return; }
        String repo = args[1], remote = args[2], ref = args[3];
        String u = opt(args, "--user");
        String p = opt(args, "--pass");
        try (Git git = open(repo)) {
            String url = git.getRepository().getConfig().getString("remote", remote, "url");
            CredentialsProvider cp = creds(url, u, p);
            FetchCommand fetch = git.fetch().setRemote(remote);
            if (cp != null) fetch.setCredentialsProvider(cp);
            fetch.call();
            String remoteRef = "refs/remotes/" + remote + "/" + ref;
            Ref r = git.getRepository().findRef(remoteRef);
            MergeResult m = git.merge().include(r).call();
            System.out.println("Merge status: " + m.getMergeStatus());
        }
    }

    private static void cmdRemote(String[] args) throws IOException, GitAPIException {
        if (args.length < 3 || !"-v".equals(args[1])) { System.err.println("remote -v <repoPath>"); return; }
        String repo = args[2];
        try (Git git = open(repo)) {
            for (RemoteConfig rc : git.remoteList().call()) {
                for (URIish u : rc.getURIs()) System.out.println(rc.getName() + "\t" + u);
            }
        }
    }

    // ---------------- helpers ----------------

    private static Git open(String repoPath) throws IOException {
        FileRepositoryBuilder b = new FileRepositoryBuilder();
        Repository repo = b.setGitDir(new File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
        return new Git(repo);
    }

    private static String opt(String[] args, String key) {
        for (int i = 0; i < args.length - 1; i++) if (key.equals(args[i])) return args[i+1];
        return null;
    }

    private static CredentialsProvider creds(String url, String explicitUser, String explicitPass) {
        if (explicitUser != null && explicitPass != null)
            return new UsernamePasswordCredentialsProvider(explicitUser, explicitPass);
        String u = System.getenv("GIT_USER");
        String p = System.getenv("GIT_PASSWORD");
        if (u != null && p != null) return new UsernamePasswordCredentialsProvider(u, p);
        Console c = System.console();
        if (c != null && url != null) {
            c.printf("Authentication required for %s%n", url);
            String iu = explicitUser != null ? explicitUser : c.readLine("Username: ");
            char[] ip = explicitPass != null ? explicitPass.toCharArray() : c.readPassword("Password: ");
            if (iu != null && ip != null) return new UsernamePasswordCredentialsProvider(iu, new String(ip));
        }
        return null;
    }

    private static void printStatus(Status s) {
        if (!s.getAdded().isEmpty()) System.out.println("Added: " + s.getAdded());
        if (!s.getChanged().isEmpty()) System.out.println("Changed: " + s.getChanged());
        if (!s.getRemoved().isEmpty()) System.out.println("Removed: " + s.getRemoved());
        if (!s.getModified().isEmpty()) System.out.println("Modified: " + s.getModified());
        if (!s.getMissing().isEmpty()) System.out.println("Missing: " + s.getMissing());
        if (!s.getUntracked().isEmpty()) System.out.println("Untracked: " + s.getUntracked());
        if (!s.hasUncommittedChanges()) System.out.println("Clean.");
    }
}
