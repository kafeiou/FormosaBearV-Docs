package com.sismics.docs.rest.resource;

import com.sismics.docs.core.constant.AuditLogType;
import com.sismics.docs.core.dao.AuditLogDao;
import com.sismics.docs.core.model.jpa.AuditLog;
import com.sismics.docs.core.service.BackupService;
import com.sismics.docs.core.service.BackupService.BackupInfo;
import com.sismics.docs.rest.constant.BaseFunction;
import com.sismics.rest.exception.ForbiddenClientException;
import com.sismics.rest.exception.ServerException;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Backup and restore REST resource.
 */
@Path("/backup")
public class BackupResource extends BaseResource {
    private static final Logger log = LoggerFactory.getLogger(BackupResource.class);

    /**
     * Returns system backup statistics and current configuration status.
     */
    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getInfo() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        BackupService backupService = new BackupService();
        BackupInfo info = backupService.getBackupInfo();

        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("database_driver", info.databaseDriver)
                .add("file_encrypt", info.fileEncrypt)
                .add("file_encrypt_locked", info.fileEncryptLocked)
                .add("file_delete", info.fileDelete)
                .add("file_duplicate", info.fileDuplicate)
                .add("file_download", info.fileDownload)
                .add("document_count", info.documentCount)
                .add("file_count", info.fileCount)
                .add("storage_size_bytes", info.storageSizeBytes);

        return Response.ok(response.build()).build();
    }

    /**
     * Exports full system backup archive as a streaming ZIP download.
     */
    @GET
    @Produces("application/zip")
    public Response exportBackup() {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        String filename = "FormosaBearV-docs_backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".zip";

        StreamingOutput streamingOutput = output -> {
            try {
                BackupService backupService = new BackupService();
                backupService.exportBackup(output);
            } catch (Exception e) {
                log.error("Error streaming backup ZIP archive", e);
                throw new WebApplicationException(e);
            }
        };

        try {
            AuditLogDao auditLogDao = new AuditLogDao();
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(principal.getId());
            auditLog.setEntityId("SYSTEM");
            auditLog.setEntityClass("System");
            auditLog.setType(AuditLogType.DOWNLOAD);
            auditLog.setMessage("Full system backup downloaded: " + filename);
            auditLogDao.create(auditLog);
        } catch (Exception e) {
            log.warn("Could not write audit log for backup download", e);
        }

        return Response.ok(streamingOutput)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    /**
     * Restores full system backup from uploaded ZIP file.
     */
    @POST
    @Path("/restore")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response restoreBackup(@FormDataParam("file") FormDataBodyPart fileBodyPart) {
        if (!authenticate()) {
            throw new ForbiddenClientException();
        }
        checkBaseFunction(BaseFunction.ADMIN);

        if (fileBodyPart == null) {
            throw new ServerException("MissingParameter", "No backup archive file was provided");
        }

        try (InputStream inputStream = fileBodyPart.getValueAs(InputStream.class)) {
            BackupService backupService = new BackupService();
            backupService.restoreBackup(inputStream);

            try {
                AuditLogDao auditLogDao = new AuditLogDao();
                AuditLog auditLog = new AuditLog();
                auditLog.setUserId(principal.getId());
                auditLog.setEntityId("SYSTEM");
                auditLog.setEntityClass("System");
                auditLog.setType(AuditLogType.UPDATE);
                auditLog.setMessage("Full system restored from backup archive");
                auditLogDao.create(auditLog);
            } catch (Exception e) {
                log.warn("Could not write audit log for backup restore", e);
            }

            JsonObjectBuilder response = Json.createObjectBuilder()
                    .add("status", "ok");
            return Response.ok(response.build()).build();
        } catch (Exception e) {
            log.error("Error restoring system backup", e);
            throw new ServerException("RestoreError", e.getMessage());
        }
    }
}
