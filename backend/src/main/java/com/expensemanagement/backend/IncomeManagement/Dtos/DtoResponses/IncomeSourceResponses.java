package com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What the app sees of an income source. */
public class IncomeSourceResponses
{
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Details
    {
        private UUID id;
        private String name;
        private String icon;
        private String color;
        private String description;
        /** How many income entries point at it - the number the delete rule quotes. */
        private long incomeCount;
    }

    /** The list, plus the names of the entries that block a delete, so the app need not ask twice. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Listing
    {
        private List<Details> items;
        private long total;
    }
}
