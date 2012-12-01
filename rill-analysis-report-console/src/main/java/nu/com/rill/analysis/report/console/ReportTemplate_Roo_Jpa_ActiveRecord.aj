// WARNING: DO NOT EDIT THIS FILE. THIS FILE IS MANAGED BY SPRING ROO.
// You may push code into the target .java compilation unit if you wish to edit any member(s).

package nu.com.rill.analysis.report.console;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import nu.com.rill.analysis.report.console.ReportTemplate;
import org.springframework.transaction.annotation.Transactional;

privileged aspect ReportTemplate_Roo_Jpa_ActiveRecord {
    
    @PersistenceContext
    transient EntityManager ReportTemplate.entityManager;
    
    public static final EntityManager ReportTemplate.entityManager() {
        EntityManager em = new ReportTemplate().entityManager;
        if (em == null) throw new IllegalStateException("Entity manager has not been injected (is the Spring Aspects JAR configured as an AJC/AJDT aspects library?)");
        return em;
    }
    
    public static long ReportTemplate.countReportTemplates() {
        return entityManager().createQuery("SELECT COUNT(o) FROM ReportTemplate o", Long.class).getSingleResult();
    }
    
    public static List<ReportTemplate> ReportTemplate.findAllReportTemplates() {
        return entityManager().createQuery("SELECT o FROM ReportTemplate o", ReportTemplate.class).getResultList();
    }
    
    public static ReportTemplate ReportTemplate.findReportTemplate(Long id) {
        if (id == null) return null;
        return entityManager().find(ReportTemplate.class, id);
    }
    
    public static List<ReportTemplate> ReportTemplate.findReportTemplateEntries(int firstResult, int maxResults) {
        return entityManager().createQuery("SELECT o FROM ReportTemplate o", ReportTemplate.class).setFirstResult(firstResult).setMaxResults(maxResults).getResultList();
    }
    
    @Transactional
    public void ReportTemplate.persist() {
        if (this.entityManager == null) this.entityManager = entityManager();
        this.entityManager.persist(this);
    }
    
    @Transactional
    public void ReportTemplate.remove() {
        if (this.entityManager == null) this.entityManager = entityManager();
        if (this.entityManager.contains(this)) {
            this.entityManager.remove(this);
        } else {
            ReportTemplate attached = ReportTemplate.findReportTemplate(this.id);
            this.entityManager.remove(attached);
        }
    }
    
    @Transactional
    public void ReportTemplate.flush() {
        if (this.entityManager == null) this.entityManager = entityManager();
        this.entityManager.flush();
    }
    
    @Transactional
    public void ReportTemplate.clear() {
        if (this.entityManager == null) this.entityManager = entityManager();
        this.entityManager.clear();
    }
    
    @Transactional
    public ReportTemplate ReportTemplate.merge() {
        if (this.entityManager == null) this.entityManager = entityManager();
        ReportTemplate merged = this.entityManager.merge(this);
        this.entityManager.flush();
        return merged;
    }
    
}
