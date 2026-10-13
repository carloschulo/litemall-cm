package org.linlinjava.litemall.admin;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linlinjava.litemall.admin.util.AdminResponseCode;
import org.linlinjava.litemall.admin.web.AdminAdminController;
import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.lang.reflect.Proxy;
import java.util.Map;

@WebAppConfiguration
@RunWith(SpringRunner.class)
@SpringBootTest
public class AdminAdminTest {

    @Autowired
    private AdminAdminController adminController;

    @Autowired
    private LitemallAdminService adminService;

    @After
    public void tearDown() {
        ThreadContext.unbindSubject();
    }

    private Subject createMockSubject(LitemallAdmin admin) {
        return (Subject) Proxy.newProxyInstance(
                AdminAdminTest.class.getClassLoader(),
                new Class[]{Subject.class},
                (proxy, method, args) -> {
                    if ("getPrincipal".equals(method.getName())) {
                        return admin;
                    }
                    return null;
                }
        );
    }

    @Test
    public void testCannotModifySuperAdmin() {
        LitemallAdmin currentAdmin = new LitemallAdmin();
        currentAdmin.setId(4);
        currentAdmin.setRoleIds(new Integer[]{3});
        ThreadContext.bind(createMockSubject(currentAdmin));

        LitemallAdmin updateAdmin = new LitemallAdmin();
        updateAdmin.setId(1);
        updateAdmin.setUsername("admin123");
        updateAdmin.setPassword("123456");

        Object result = adminController.update(updateAdmin);
        Map map = (Map) result;
        Assert.assertEquals(AdminResponseCode.ADMIN_ALTER_NOT_ALLOWED, map.get("errno"));
    }

    @Test
    public void testNonSuperAdminCannotAssignSuperRole() {
        LitemallAdmin currentAdmin = new LitemallAdmin();
        currentAdmin.setId(4);
        currentAdmin.setRoleIds(new Integer[]{3});
        ThreadContext.bind(createMockSubject(currentAdmin));

        LitemallAdmin updateAdmin = new LitemallAdmin();
        updateAdmin.setId(4);
        updateAdmin.setUsername("promotion123");
        updateAdmin.setPassword("123456");
        updateAdmin.setRoleIds(new Integer[]{1});

        Object result = adminController.update(updateAdmin);
        Map map = (Map) result;
        Assert.assertEquals(AdminResponseCode.ADMIN_ALTER_NOT_ALLOWED, map.get("errno"));
    }

    @Test
    public void testAdminCannotModifyOwnRole() {
        LitemallAdmin currentAdmin = new LitemallAdmin();
        currentAdmin.setId(4);
        currentAdmin.setRoleIds(new Integer[]{3});
        ThreadContext.bind(createMockSubject(currentAdmin));

        LitemallAdmin updateAdmin = new LitemallAdmin();
        updateAdmin.setId(4);
        updateAdmin.setUsername("promotion123");
        updateAdmin.setPassword("123456");
        updateAdmin.setAvatar("https://example.com/new-avatar.png");
        // Attempting to change own role to role 2 (mall manager)
        updateAdmin.setRoleIds(new Integer[]{2});

        Object result = adminController.update(updateAdmin);
        Map map = (Map) result;
        Assert.assertEquals(Integer.valueOf(0), map.get("errno"));

        // Verify that the database role was not changed to 2, it remains original [3]
        LitemallAdmin dbAdmin = adminService.findById(4);
        Assert.assertArrayEquals(new Integer[]{3}, dbAdmin.getRoleIds());
    }
}
