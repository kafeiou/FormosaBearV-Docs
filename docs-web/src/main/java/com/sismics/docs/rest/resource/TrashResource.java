package com.sismics.docs.rest.resource;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.model.context.AppContext;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.ConfigUtil;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.util.FileUtil;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ClientException;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import com.sismics.util.JsonUtil;
import com.sismics.util.context.ThreadLocalContext;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trash / Recycle Bin REST resource.
 */
@Path("/trash")
public class TrashResource extends BaseResource {
    private static final Logger log = LoggerFactory.getLogger(TrashResource.class);

    /**
     * Lists soft-deleted documents and individually soft-deleted files in the Trash.
     *
     * @param all If true and caller is admin, list all users' trash items
     * @return Response
     */
    @GET
    public Response list(@QueryParam("all") Boolean all) {
        if (!authenticate() || "guest".equals(principal.getName())) {
            throw new ForbiddenClientException();
        }

        boolean isAdmin = hasBaseFunction(BaseFunction.ADMIN);
        boolean showAll = isAdmin && Boolean.TRUE.equals(all);

        EntityManager em = ThreadLocalContext.get().getEntityManager();
        FileDao fileDao = new FileDao();

        // 1. Fetch soft-deleted documents
        StringBuilder docSql = new StringBuilder(
                "select d.DOC_ID_C, d.DOC_TITLE_C, d.DOC_LANGUAGE_C, d.DOC_CREATEDATE_D, d.DOC_DELETEDATE_D, u.USE_USERNAME_C, d.DOC_IDUSER_C " +
                "from T_DOCUMENT d " +
                "join T_USER u on d.DOC_IDUSER_C = u.USE_ID_C " +
                "where d.DOC_DELETEDATE_D is not null ");
        if (!showAll) {
            docSql.append("and d.DOC_IDUSER_C = :userId ");
        }
        docSql.append("order by d.DOC_DELETEDATE_D desc");

        Query docQuery = em.createNativeQuery(docSql.toString());
        if (!showAll) {
            docQuery.setParameter("userId", principal.getId());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> docRows = docQuery.getResultList();
        JsonArrayBuilder documentsBuilder = Json.createArrayBuilder();
        for (Object[] row : docRows) {
            int i = 0;
            String docId = (String) row[i++];
            String title = (String) row[i++];
            String language = (String) row[i++];
            Timestamp createTs = (Timestamp) row[i++];
            Timestamp deleteTs = (Timestamp) row[i++];
            String creator = (String) row[i++];
            if (deleteTs == null || deleteTs.getTime() <= 0L) {
                continue;
            }

            List<File> docFiles = fileDao.getDeletedWithDocument(docId, new Date(deleteTs.getTime()));
            long fileCount = docFiles.stream().filter(File::isLatestVersion).count();
            long totalSize = docFiles.stream()
                    .mapToLong(f -> (f.getSize() != null && f.getSize() > 0) ? f.getSize() : 0L)
                    .sum();

            documentsBuilder.add(Json.createObjectBuilder()
                    .add("id", docId)
                    .add("title", JsonUtil.nullable(title))
                    .add("language", JsonUtil.nullable(language))
                    .add("create_date", createTs != null ? createTs.getTime() : 0L)
                    .add("delete_date", deleteTs.getTime())
                    .add("creator", JsonUtil.nullable(creator))
                    .add("file_count", fileCount)
                    .add("total_size", totalSize));
        }

        // 2. Fetch individually soft-deleted files (not deleted as part of parent document deletion)
        StringBuilder fileSql = new StringBuilder(
                "select f.FIL_ID_C, f.FIL_NAME_C, f.FIL_MIMETYPE_C, f.FIL_SIZE_N, f.FIL_VERSION_N, f.FIL_VERSION_LABEL_C, " +
                "f.FIL_LATESTVERSION_B, f.FIL_CREATEDATE_D, f.FIL_DELETEDATE_D, u.USE_USERNAME_C, " +
                "f.FIL_IDDOC_C, d.DOC_TITLE_C, d.DOC_DELETEDATE_D " +
                "from T_FILE f " +
                "join T_USER u on f.FIL_IDUSER_C = u.USE_ID_C " +
                "left join T_DOCUMENT d on f.FIL_IDDOC_C = d.DOC_ID_C " +
                "where f.FIL_DELETEDATE_D is not null ");
        if (!showAll) {
            fileSql.append("and (f.FIL_IDUSER_C = :userId or d.DOC_IDUSER_C = :userId) ");
        }
        fileSql.append("order by f.FIL_DELETEDATE_D desc");

        Query fileQuery = em.createNativeQuery(fileSql.toString());
        if (!showAll) {
            fileQuery.setParameter("userId", principal.getId());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> fileRows = fileQuery.getResultList();
        JsonArrayBuilder filesBuilder = Json.createArrayBuilder();
        for (Object[] row : fileRows) {
            int i = 0;
            String fileId = (String) row[i++];
            String name = (String) row[i++];
            String mimeType = (String) row[i++];
            Number sizeNum = (Number) row[i++];
            Number versionNum = (Number) row[i++];
            String versionLabel = (String) row[i++];
            Object latestVersionObj = row[i++];
            Timestamp createTs = (Timestamp) row[i++];
            Timestamp fileDeleteTs = (Timestamp) row[i++];
            String creator = (String) row[i++];
            String docId = (String) row[i++];
            String docTitle = (String) row[i++];
            Timestamp docDeleteTs = (Timestamp) row[i];

            if (fileDeleteTs == null || fileDeleteTs.getTime() <= 0L) {
                continue;
            }

            // Exclude files that were deleted at the same time as (or after) their parent document
            if (docId != null && docDeleteTs != null && docDeleteTs.getTime() > 0) {
                if (fileDeleteTs.getTime() >= docDeleteTs.getTime() - 1000L) {
                    continue;
                }
            }
            // Also exclude files whose parent document was permanently purged at the same time
            if (docId != null && docDeleteTs != null && docDeleteTs.getTime() == 0L && fileDeleteTs.getTime() == 0L) {
                continue;
            }

            boolean latestVersion = false;
            if (latestVersionObj instanceof Boolean) {
                latestVersion = (Boolean) latestVersionObj;
            } else if (latestVersionObj instanceof Number) {
                latestVersion = ((Number) latestVersionObj).intValue() != 0;
            }

            boolean docDeleted = docDeleteTs != null && docDeleteTs.getTime() > 0;

            filesBuilder.add(Json.createObjectBuilder()
                    .add("id", fileId)
                    .add("name", JsonUtil.nullable(name))
                    .add("mimetype", JsonUtil.nullable(mimeType))
                    .add("size", sizeNum != null && sizeNum.longValue() > 0 ? sizeNum.longValue() : 0L)
                    .add("version", versionNum != null ? versionNum.intValue() : 0)
                    .add("version_label", JsonUtil.nullable(versionLabel))
                    .add("latest_version", latestVersion)
                    .add("create_date", createTs != null ? createTs.getTime() : 0L)
                    .add("delete_date", fileDeleteTs.getTime())
                    .add("creator", JsonUtil.nullable(creator))
                    .add("document_id", JsonUtil.nullable(docId))
                    .add("document_title", JsonUtil.nullable(docTitle))
                    .add("document_deleted", docDeleted));
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("file_delete", ConfigUtil.isFileDelete())
                .add("is_admin", isAdmin)
                .add("documents", documentsBuilder)
                .add("files", filesBuilder);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Restores a soft-deleted document and its attached files from the Trash.
     *
     * @param id Document ID
     * @return Response
     */
    @POST
    @Path("document/{id: [a-z0-9\\-]+}/restore")
    public Response restoreDocument(@PathParam("id") String id) {
        if (!authenticate() || "guest".equals(principal.getName())) {
            throw new ForbiddenClientException();
        }

        DocumentDao documentDao = new DocumentDao();
        Document document = documentDao.getDeletedById(id);
        if (document == null) {
            throw new NotFoundException();
        }

        boolean isAdmin = hasBaseFunction(BaseFunction.ADMIN);
        if (!isAdmin && !principal.getId().equals(document.getUserId())) {
            throw new ForbiddenClientException();
        }

        FileDao fileDao = new FileDao();
        List<File> filesToRestore = fileDao.getDeletedWithDocument(id, document.getDeleteDate());

        // Guardrail 1: Check duplicate filenames when duplicate files are disallowed
        if (!ConfigUtil.canFileDuplicate()) {
            for (File file : filesToRestore) {
                if (file.isLatestVersion() && !Strings.isNullOrEmpty(file.getName())) {
                    if (fileDao.getByUserIdFileName(file.getUserId(), file.getName()) != null) {
                        throw new ClientException("FileNameDuplicate", "A file with the same name already exists: " + file.getName());
                    }
                }
            }
        }

        // Guardrail 2: Check user and global storage quotas
        UserDao userDao = new UserDao();
        Map<String, Long> bytesByOwner = new HashMap<>();
        long totalRestoredBytes = 0L;
        for (File file : filesToRestore) {
            long size = (file.getSize() != null && file.getSize() > 0) ? file.getSize() : 0L;
            bytesByOwner.merge(file.getUserId(), size, Long::sum);
            totalRestoredBytes += size;
        }

        for (Map.Entry<String, Long> entry : bytesByOwner.entrySet()) {
            User owner = userDao.getById(entry.getKey());
            if (owner != null && owner.getStorageQuota() > 0 && owner.getStorageCurrent() + entry.getValue() > owner.getStorageQuota()) {
                throw new ClientException("QuotaReached", "Storage quota reached");
            }
        }
        String globalStorageQuotaStr = System.getenv(Constants.GLOBAL_QUOTA_ENV);
        if (!Strings.isNullOrEmpty(globalStorageQuotaStr) && totalRestoredBytes > 0) {
            long globalStorageQuota = Long.parseLong(globalStorageQuotaStr);
            long globalStorageCurrent = userDao.getGlobalStorageCurrent();
            if (globalStorageCurrent + totalRestoredBytes > globalStorageQuota) {
                throw new ClientException("QuotaReached", "Global storage quota reached");
            }
        }

        // Move physical files from delete/storage back to storage
        try {
            for (File file : filesToRestore) {
                FileUtil.restore(file);
            }
        } catch (IOException e) {
            throw new ServerException("RestoreError", "Error restoring physical files from trash", e);
        }

        // Restore database records
        Document restoredDoc = documentDao.restore(id, principal.getId());

        // Update owner storage quotas
        for (Map.Entry<String, Long> entry : bytesByOwner.entrySet()) {
            if (entry.getValue() > 0) {
                User owner = userDao.getById(entry.getKey());
                if (owner != null) {
                    owner.setStorageCurrent(owner.getStorageCurrent() + entry.getValue());
                    userDao.updateQuota(owner);
                }
            }
        }

        // Update main file ID and rebuild search index for restored document and files
        List<File> activeFiles = fileDao.getByDocumentId(null, id);
        if (activeFiles.isEmpty()) {
            restoredDoc.setFileId(null);
        } else {
            restoredDoc.setFileId(activeFiles.get(0).getId());
        }
        documentDao.updateFileId(restoredDoc);

        try {
            AppContext.getInstance().getIndexingHandler().createDocument(restoredDoc);
            for (File file : activeFiles) {
                AppContext.getInstance().getIndexingHandler().createFile(file);
            }
        } catch (Exception e) {
            log.warn("Error indexing restored document " + id, e);
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok")
                .add("id", id);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Permanently purges a soft-deleted document and its files from the Trash.
     *
     * @param id Document ID
     * @return Response
     */
    @DELETE
    @Path("document/{id: [a-z0-9\\-]+}")
    public Response purgeDocument(@PathParam("id") String id) {
        if (!authenticate() || "guest".equals(principal.getName())) {
            throw new ForbiddenClientException();
        }

        DocumentDao documentDao = new DocumentDao();
        Document document = documentDao.getDeletedById(id);
        if (document == null) {
            throw new NotFoundException();
        }

        boolean isAdmin = hasBaseFunction(BaseFunction.ADMIN);
        if (!isAdmin && !principal.getId().equals(document.getUserId())) {
            throw new ForbiddenClientException();
        }

        FileDao fileDao = new FileDao();
        List<File> deletedFiles = fileDao.getAllSoftDeletedByDocumentId(id);
        for (File file : deletedFiles) {
            try {
                FileUtil.purge(file);
            } catch (IOException e) {
                log.warn("Error purging physical file " + file.getId(), e);
            }
        }

        documentDao.purge(id, principal.getId());

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Restores an individually soft-deleted file from the Trash.
     *
     * @param id File ID
     * @return Response
     */
    @POST
    @Path("file/{id: [a-z0-9\\-]+}/restore")
    public Response restoreFile(@PathParam("id") String id) {
        if (!authenticate() || "guest".equals(principal.getName())) {
            throw new ForbiddenClientException();
        }

        FileDao fileDao = new FileDao();
        File file = fileDao.getDeletedById(id);
        if (file == null) {
            throw new NotFoundException();
        }

        DocumentDao documentDao = new DocumentDao();
        Document parentDoc = file.getDocumentId() != null ? documentDao.getByIdIncludeDeleted(file.getDocumentId()) : null;

        boolean isAdmin = hasBaseFunction(BaseFunction.ADMIN);
        boolean isOwner = principal.getId().equals(file.getUserId()) ||
                (parentDoc != null && principal.getId().equals(parentDoc.getUserId()));
        if (!isAdmin && !isOwner) {
            throw new ForbiddenClientException();
        }

        // Guardrail 1: Check parent document status
        boolean detachFromPurgedDoc = false;
        if (file.getDocumentId() != null) {
            if (parentDoc != null && parentDoc.getDeleteDate() != null && parentDoc.getDeleteDate().getTime() > 0) {
                throw new ClientException("ParentDocumentInTrash", "The parent document is currently in the trash. Please restore the document first.");
            }
            if (parentDoc == null || parentDoc.getDeleteDate() != null) {
                detachFromPurgedDoc = true;
            }
        }

        // Guardrail 2: Check duplicate filename when duplicate files are disallowed
        if (!ConfigUtil.canFileDuplicate() && file.isLatestVersion() && !Strings.isNullOrEmpty(file.getName())) {
            if (fileDao.getByUserIdFileName(file.getUserId(), file.getName()) != null) {
                throw new ClientException("FileNameDuplicate", "A file with the same name already exists: " + file.getName());
            }
        }

        // Guardrail 3: Check storage quota
        UserDao userDao = new UserDao();
        User owner = userDao.getById(file.getUserId());
        long fileSize = (file.getSize() != null && file.getSize() > 0) ? file.getSize() : 0L;
        if (owner != null && owner.getStorageQuota() > 0 && owner.getStorageCurrent() + fileSize > owner.getStorageQuota()) {
            throw new ClientException("QuotaReached", "Storage quota reached");
        }
        String globalStorageQuotaStr = System.getenv(Constants.GLOBAL_QUOTA_ENV);
        if (!Strings.isNullOrEmpty(globalStorageQuotaStr) && fileSize > 0) {
            long globalStorageQuota = Long.parseLong(globalStorageQuotaStr);
            long globalStorageCurrent = userDao.getGlobalStorageCurrent();
            if (globalStorageCurrent + fileSize > globalStorageQuota) {
                throw new ClientException("QuotaReached", "Global storage quota reached");
            }
        }

        // Restore physical file
        try {
            if (detachFromPurgedDoc) {
                java.nio.file.Path deleteDir = DirectoryUtil.getDeleteStorageDirectory(file);
                file.setDocumentId(null);
                java.nio.file.Path storageDir = DirectoryUtil.getStorageDirectory(file);
                Files.createDirectories(storageDir);
                if (file.getName() != null) {
                    String[] suffixes = new String[] { "", "_web", "_thumb", "_pdf" };
                    for (String suffix : suffixes) {
                        java.nio.file.Path src = deleteDir.resolve(file.getName() + suffix);
                        if (Files.exists(src)) {
                            Files.move(src, storageDir.resolve(file.getName() + suffix), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } else {
                FileUtil.restore(file);
            }
        } catch (IOException e) {
            throw new ServerException("RestoreError", "Error restoring physical file from trash", e);
        }

        // Restore database record
        File restoredFile = fileDao.restore(id, principal.getId());
        if (detachFromPurgedDoc) {
            restoredFile.setDocumentId(null);
            restoredFile.setLatestVersion(true);
            restoredFile.setVersion(0);
            restoredFile.setVersionId(null);
            fileDao.update(restoredFile);
        } else if (restoredFile.getVersionId() != null) {
            // Reconcile version numbers and latestVersion flag for the version chain
            List<File> versions = fileDao.getByVersionId(restoredFile.getVersionId());
            versions.sort(Comparator.comparing(File::getCreateDate));
            for (int i = 0; i < versions.size(); i++) {
                File v = versions.get(i);
                v.setVersion(i);
                v.setLatestVersion(i == versions.size() - 1);
                fileDao.update(v);
            }
        }

        // Update owner storage quota
        if (owner != null && fileSize > 0) {
            owner.setStorageCurrent(owner.getStorageCurrent() + fileSize);
            userDao.updateQuota(owner);
        }

        // Re-index file and update parent document if applicable
        try {
            if (restoredFile.isLatestVersion()) {
                AppContext.getInstance().getIndexingHandler().createFile(restoredFile);
            }
            if (restoredFile.getDocumentId() != null) {
                Document activeDoc = documentDao.getById(restoredFile.getDocumentId());
                if (activeDoc != null) {
                    List<File> activeDocFiles = fileDao.getByDocumentId(null, activeDoc.getId());
                    activeDoc.setFileId(activeDocFiles.isEmpty() ? null : activeDocFiles.get(0).getId());
                    documentDao.updateFileId(activeDoc);
                    AppContext.getInstance().getIndexingHandler().updateDocument(activeDoc);
                }
            }
        } catch (Exception e) {
            log.warn("Error indexing restored file " + id, e);
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok")
                .add("id", id);
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Permanently purges an individually soft-deleted file from the Trash.
     *
     * @param id File ID
     * @return Response
     */
    @DELETE
    @Path("file/{id: [a-z0-9\\-]+}")
    public Response purgeFile(@PathParam("id") String id) {
        if (!authenticate() || "guest".equals(principal.getName())) {
            throw new ForbiddenClientException();
        }

        FileDao fileDao = new FileDao();
        File file = fileDao.getDeletedById(id);
        if (file == null) {
            throw new NotFoundException();
        }

        DocumentDao documentDao = new DocumentDao();
        Document parentDoc = file.getDocumentId() != null ? documentDao.getByIdIncludeDeleted(file.getDocumentId()) : null;

        boolean isAdmin = hasBaseFunction(BaseFunction.ADMIN);
        boolean isOwner = principal.getId().equals(file.getUserId()) ||
                (parentDoc != null && principal.getId().equals(parentDoc.getUserId()));
        if (!isAdmin && !isOwner) {
            throw new ForbiddenClientException();
        }

        try {
            FileUtil.purge(file);
        } catch (IOException e) {
            log.warn("Error purging physical file " + id, e);
        }

        fileDao.purge(id, principal.getId());

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok");
        return Response.ok().entity(response.build()).build();
    }

    /**
     * Empties the Trash (permanently purges all soft-deleted documents and files in the current scope).
     *
     * @param all If true and caller is admin, empty all users' trash
     * @return Response
     */
    @DELETE
    @Path("empty")
    public Response emptyTrash(@QueryParam("all") Boolean all) {
        if (!authenticate() || "guest".equals(principal.getName())) {
            throw new ForbiddenClientException();
        }

        boolean isAdmin = hasBaseFunction(BaseFunction.ADMIN);
        boolean purgeAll = isAdmin && Boolean.TRUE.equals(all);

        EntityManager em = ThreadLocalContext.get().getEntityManager();
        DocumentDao documentDao = new DocumentDao();
        FileDao fileDao = new FileDao();

        // 1. Purge all matching soft-deleted documents
        StringBuilder docSql = new StringBuilder(
                "select d.DOC_ID_C from T_DOCUMENT d where d.DOC_DELETEDATE_D is not null ");
        if (!purgeAll) {
            docSql.append("and d.DOC_IDUSER_C = :userId ");
        }
        Query docQuery = em.createNativeQuery(docSql.toString());
        if (!purgeAll) {
            docQuery.setParameter("userId", principal.getId());
        }
        @SuppressWarnings("unchecked")
        List<String> docIds = docQuery.getResultList();
        int purgedDocCount = 0;
        for (String docId : docIds) {
            Document d = documentDao.getDeletedById(docId);
            if (d == null) {
                continue;
            }
            List<File> docFiles = fileDao.getAllSoftDeletedByDocumentId(docId);
            for (File f : docFiles) {
                try {
                    FileUtil.purge(f);
                } catch (IOException e) {
                    log.warn("Error purging physical file " + f.getId(), e);
                }
            }
            documentDao.purge(docId, principal.getId());
            purgedDocCount++;
        }

        // 2. Purge all remaining matching soft-deleted files
        StringBuilder fileSql = new StringBuilder(
                "select f.FIL_ID_C from T_FILE f left join T_DOCUMENT d on f.FIL_IDDOC_C = d.DOC_ID_C " +
                "where f.FIL_DELETEDATE_D is not null ");
        if (!purgeAll) {
            fileSql.append("and (f.FIL_IDUSER_C = :userId or d.DOC_IDUSER_C = :userId) ");
        }
        Query fileQuery = em.createNativeQuery(fileSql.toString());
        if (!purgeAll) {
            fileQuery.setParameter("userId", principal.getId());
        }
        @SuppressWarnings("unchecked")
        List<String> fileIds = fileQuery.getResultList();
        int purgedFileCount = 0;
        for (String fileId : fileIds) {
            File f = fileDao.getDeletedById(fileId);
            if (f != null) {
                try {
                    FileUtil.purge(f);
                } catch (IOException e) {
                    log.warn("Error purging physical file " + f.getId(), e);
                }
                fileDao.purge(fileId, principal.getId());
                purgedFileCount++;
            }
        }

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("status", "ok")
                .add("purged_documents", purgedDocCount)
                .add("purged_files", purgedFileCount);
        return Response.ok().entity(response.build()).build();
    }
}
