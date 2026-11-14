package com.sismics.docs.core.dao.jpa;

import com.sismics.docs.BaseTransactionalTest;
import com.sismics.docs.core.dao.ShareDao;
import com.sismics.docs.core.dao.UserDao;
import com.sismics.docs.core.model.jpa.Share;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.docs.core.util.TransactionUtil;
import com.sismics.docs.core.util.authentication.InternalAuthenticationHandler;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the persistance layer.
 * 
 * @author jtremeaux
 */
public class TestJpa extends BaseTransactionalTest {
    @Test
    public void testJpa() throws Exception {
        // Create a user
        UserDao userDao = new UserDao();
        User user = createUser("testJpa");

        TransactionUtil.commit();

        // Search a user by his ID
        user = userDao.getById(user.getId());
        Assert.assertNotNull(user);
        Assert.assertEquals("toto@docs.com", user.getEmail());

        // Authenticate using the database
        Assert.assertNotNull(new InternalAuthenticationHandler().authenticate("testJpa", "12345678"));

        // Delete the created user
        userDao.delete("testJpa", user.getId());
        TransactionUtil.commit();

        // Test ShareDao allowExport default (false) and update persistence
        ShareDao shareDao = new ShareDao();
        Share share = new Share();
        share.setName("default-preview-only");
        String shareId = shareDao.create(share);
        TransactionUtil.commit();

        Share savedShare = shareDao.getActiveById(shareId);
        Assert.assertNotNull(savedShare);
        Assert.assertFalse("Share allowExport must default to false", savedShare.isAllowExport());

        savedShare.setAllowExport(true);
        shareDao.update(savedShare);
        TransactionUtil.commit();

        Share updatedShare = shareDao.getActiveById(shareId);
        Assert.assertNotNull(updatedShare);
        Assert.assertTrue("Share allowExport must persist true after update", updatedShare.isAllowExport());

        shareDao.delete(shareId);
        TransactionUtil.commit();
        Assert.assertNull(shareDao.getActiveById(shareId));
    }
}
