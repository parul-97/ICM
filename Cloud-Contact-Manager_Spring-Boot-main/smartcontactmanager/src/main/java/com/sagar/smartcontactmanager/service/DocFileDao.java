package com.sagar.smartcontactmanager.service;

import com.sagar.smartcontactmanager.entities.FileDocument;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocFileDao extends CrudRepository<FileDocument, Long> {

    FileDocument findByFileName(String fileName);
}
