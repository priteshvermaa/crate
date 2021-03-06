.. highlight:: psql
.. _ref-alter-table:

===============
``ALTER TABLE``
===============

Alter an existing table.

.. rubric:: Table of Contents

.. contents::
   :local:

Synopsis
========

::

    ALTER [ BLOB ] TABLE { ONLY table_ident
                           | table_ident [ PARTITION (partition_column = value [ , ... ]) ] }
      { SET ( parameter = value [ , ... ] )
        | RESET ( parameter [ , ... ] )
        | ADD [ COLUMN ] column_name data_type [ column_constraint [ ... ] ]
        | OPEN
        | CLOSE
        | RENAME TO table_ident
        | REROUTE reroute_option
      }

where ``column_constraint`` is::

    { PRIMARY KEY |
      NOT NULL |
      INDEX { OFF | USING { PLAIN |
                            FULLTEXT [ WITH ( analyzer = analyzer_name ) ]  }
    }


Description
===========

``ALTER TABLE`` can be used to modify an existing table definition. It provides
options to add columns, modify constraints, enabling or disabling
table parameters and allows to execute a shard reroute allocation.

Use the ``BLOB`` keyword in order to alter a blob table (see
:ref:`blob_support`). Blob tables cannot have custom columns which means that
the ``ADD COLUMN`` keyword won't work.

While altering a partitioned table, using ``ONLY`` will apply changes for the
table **only** and not for any possible existing partitions. So these changes
will only be applied to new partitions. The ``ONLY`` keyword cannot be used
together with a `PARTITION`_ clause.

See the CREATE TABLE :ref:`with_clause` for a list of available parameters.

:table_ident:
  The name (optionally schema-qualified) of the table to alter.

.. _ref-alter-table-partition-clause:

Clauses
=======

``PARTITION``
-------------

If the table is partitioned this clause can be used to alter only a single
partition.

.. NOTE::

   BLOB tables cannot be partitioned and hence this clause cannot be used.

This clause identifies a single partition. It takes one or more partition
columns with a value each to identify the partition to alter.

::

    [ PARTITION ( partition_column = value [ , ... ] ) ]

:partition_column:
  The name of the column by which the table is partitioned.

  All partition columns that were part of the :ref:`partitioned_by_clause` of
  the :ref:`ref-create-table` statement must be specified.

:value:
  The columns value.

.. SEEALSO:: :ref:`Alter Partitioned Tables <partitioned_tables_alter>`


Arguments
=========

``SET/RESET``
-------------

Can be used to change a table parameter to a different value.
Using ``RESET`` will reset the parameter to its default value.

:parameter:
  The name of the parameter that is set to a new value or its default.

The supported parameters are listed in the :ref:`CREATE TABLE WITH CLAUSE
<with_clause>` documentation. 


``ADD COLUMN``
--------------

Can be used to add an additional column to a table. While columns can be added
at any time, adding a new :ref:`generated column <ref-generated-columns>` is
only possible if the table is empty.

:data_type:
  Data type of the column which should be added.

:column_name:
  Name of the column which should be added.

``OPEN/CLOSE``
--------------

Can be used to open or close the table, respectively. Closing a table prevents
all operations, except ``ALTER TABLE ... OPEN``, to fail. Operations on closed
partitions will not produce an exception, but will have no effect. Similarly,
like ``SELECT`` and ``INSERT`` on partitioned will exclude closed partitions and
continue working.

``RENAME TO``
-------------
Can be used to rename a table, while maintaining its schema and data. During
this operation the table will be closed, and all operations upon the table will
fail until the rename operation is completed.

.. _alter_table_reroute:

``REROUTE``
-----------

The ``REROUTE`` command provides various options to manually control the
allocation of shards. It allows the enforcement of explicit allocations,
cancellations and the moving of shards between nodes in a cluster. See
:ref:`ddl_reroute_shards` to get the convenient use-cases.

The rowcount defines if the reroute or allocation process of a shard was
acknowledged or rejected.

.. NOTE::

   Partitioned tables require a :ref:`Partition Clause <ref-alter-table-partition-clause>`
   in order to specify a unique ``shard_id``.

::

    [ REROUTE reroute_option]


where ``reroute_option`` is::

    { MOVE SHARD shard_id FROM node_id TO node_id
      | ALLOCATE REPLICA SHARD shard_id ON node_id
      | CANCEL SHARD shard_id ON node_id [ WITH (allow_primary = {TRUE|FALSE}) ]
    }

:shard_id:
  The shard id. Ranges from 0 up to the specified number of :ref:`sys-shards`
  shards of a table.

:node_id:
  The node ID within the cluster.

  See :ref:`sys-nodes` how to gain the unique ID.


``REROUTE`` suports the following options to start/stop shard allocation:

**MOVE**
  A started shard gets moved from one node to another. It requests a
  ``table_ident`` and a ``shard_id`` to identify the shard that receives
  the new allocation. Specify ``FROM node_id`` for the node to move the
  shard from and ``TO node_id`` to move the shard to.

**ALLOCATE REPLICA**
  Allows to force allocation of an unassigned replica shard on a specific node.

**CANCEL**
  This cancels the allocation/recovery of a ``shard_id`` of a
  ``table_ident`` on a given ``node_id``. The ``allow_primary`` flag
  indicates if it is allowed to cancel the allocation of a primary shard.
