package com.minigit.service;

import java.io.File;
import java.util.List;

/**
 * 仓库服务接口
 */
public interface RepositoryService {

    /**
     * 创建裸仓库
     * @param name 仓库名称
     * @return 创建的仓库目录
     */
    File createRepository(String name);

    /**
     * 列出所有仓库
     * @return 仓库名称列表
     */
    List<String> listRepositories();

    /**
     * 检查仓库是否存在
     * @param name 仓库名称
     * @return 是否存在
     */
    boolean repositoryExists(String name);

    /**
     * 获取仓库路径
     * @param name 仓库名称
     * @return 仓库文件对象
     */
    File getRepositoryPath(String name);

    /**
     * 验证仓库名称是否合法
     * @param name 仓库名称
     * @return 是否合法
     */
    boolean isValidRepositoryName(String name);

    /**
     * 标准化仓库名称（添加.git后缀等）
     * @param name 原始名称
     * @return 标准化后的名称
     */
    String normalizeRepositoryName(String name);
}