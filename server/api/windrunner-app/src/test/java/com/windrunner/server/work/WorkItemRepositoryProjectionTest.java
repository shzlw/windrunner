package com.windrunner.server.work;

import com.windrunner.server.work.persistence.WorkItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.jdbc.core.DataClassRowMapper;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import javax.sql.rowset.RowSetMetaDataImpl;
import java.lang.reflect.Method;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemRepositoryProjectionTest {

    @Test
    void mapsDueDateSummaryUsingTheRepositoryColumnAliases() throws Exception {
        CachedRowSet resultSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(3);
        setBigintColumn(metadata, 1, "with_due_date");
        setBigintColumn(metadata, 2, "overdue");
        setBigintColumn(metadata, 3, "due_within_next7_days");
        resultSet.setMetaData(metadata);
        resultSet.moveToInsertRow();
        resultSet.updateLong(1, 8);
        resultSet.updateLong(2, 2);
        resultSet.updateLong(3, 3);
        resultSet.insertRow();
        resultSet.moveToCurrentRow();
        resultSet.beforeFirst();

        assertThat(resultSet.next()).isTrue();
        WorkItemRepository.DueDateSummary summary = DataClassRowMapper
                .newInstance(WorkItemRepository.DueDateSummary.class)
                .mapRow(resultSet, 1);

        assertThat(summary).isEqualTo(new WorkItemRepository.DueDateSummary(8, 2, 3));
    }

    @Test
    void keepsTheNumericSafeDueDateSummaryAlias() throws Exception {
        Method method = WorkItemRepository.class.getMethod("summarizeDueDates", String.class);
        String query = method.getAnnotation(Query.class).value();

        assertThat(query).contains("AS due_within_next7_days");
    }

    private static void setBigintColumn(RowSetMetaDataImpl metadata, int index, String name) throws Exception {
        metadata.setColumnName(index, name);
        metadata.setColumnLabel(index, name);
        metadata.setColumnType(index, Types.BIGINT);
        metadata.setNullable(index, RowSetMetaDataImpl.columnNullable);
    }
}
