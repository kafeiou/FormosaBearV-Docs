package com.sismics.docs.core.dao;

import com.sismics.docs.BaseTransactionalTest;
import com.sismics.docs.core.model.jpa.AuthenticationToken;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.util.context.ThreadLocalContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Test of the authentication token DAO.
 */
public class TestAuthenticationTokenDao extends BaseTransactionalTest {
    @Test
    public void testNullAndEmptyTokenGuards() throws Exception {
        User user = createUser("auth_token_guard_user");

        AuthenticationTokenDao dao = new AuthenticationTokenDao();
        AuthenticationToken token = new AuthenticationToken();
        token.setUserId(user.getId());
        token.setLongLasted(false);
        String tokenId = dao.create(token);
        ThreadLocalContext.get().getEntityManager().flush();

        // Guarded updateLastConnectionDate should be a safe no-op for null / empty IDs (#786)
        dao.updateLastConnectionDate(null);
        dao.updateLastConnectionDate("");
        AuthenticationToken reloaded = dao.get(tokenId);
        Assert.assertNotNull(reloaded);
        Assert.assertNull(reloaded.getLastConnectionDate());

        // Guarded deleteByUserId(userId, null/empty) should delegate to deleteByUserId(userId)
        dao.deleteByUserId(user.getId(), null);
        ThreadLocalContext.get().getEntityManager().flush();
        List<AuthenticationToken> remaining = dao.getByUserId(user.getId());
        Assert.assertTrue(remaining.isEmpty());
    }
}
