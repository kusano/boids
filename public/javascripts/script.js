$(function() {

  $("#createAddress").click(function() {
    $("#createAddress").prop("disabled", true);
    $.ajax({
      type: "POST",
      url: "/address",
      success: function(data) {
        if (data.success) {
          orderBy = "modified_at";
          order = "desc";
          $("#filter input").val("");
          $("#includeInactive").prop("checked", false);
          updateHeader();
          listAddress(true);
        } else
          alert(data.error);
      },
      error: function() {
        alert("error");
      },
      complete: function() {
        $("#createAddress").prop("disabled", false);
      }
    });
  });

  function createAddressRow(address) {
    var memo = $("<form>")

    var showControl = function() {
      if (memo.find("input").val() != address.memo) {
        memo.find("div").css("display", "block");
      }
    }

    memo
      .append($("<input class='form-control' style='margin: -4px 0'>")
        .val(address.memo)
        .prop("disabled", !address.active)
        .bind("change", showControl)
        .bind("keyup", showControl))
      .append($("<div class='text-right' style='margin-top: 8px; display: none'>"+
        "<button type='reset' class='btn btn-default'>キャンセル</button>&nbsp;"+
        "<button type='submit' class='btn btn-primary'>保存</button>&nbsp;"+
        "</div>"))
      .submit(submitMemo.bind(memo, address))
      .bind("reset", function(event) {
        event.preventDefault();
        memo.find("input").val(address.memo);
        memo.find("div").css("display", "none");
      })
    
    var button = address.active ?
      $("<button class='btn btn-danger btn-xs'><span class='glyphicon glyphicon-remove'></span></button>") :
      $("<button class='btn btn-primary btn-xs'>有効化</button>");
    button.click(activeAddress.bind(button, address));

    var addr = address.address+"@"+$("#domain").val();

    return $("<tr>")
      .attr("id", address.address)
      .attr("class", address.active ? "" : "active")
      .append($("<td>")
        .append(address.active ?
          $("<a class='address'>")
            .attr("href", "mailto:"+addr)
            .text(addr) :
          $("<span>")
            .text(addr)))
      .append($("<td>")
        .append(memo))
      .append($("<td>")
        .text(address.created_at))
      .append($("<td>")
        .text(address.modified_at))
      .append($("<td>")
        .append(button));
  }

  function listAddress(init) {
    $.ajax({
      type: "POST",
      url: "/list",
      data: {
        filter: $("#filter input").val(),
        includeInactive: $("#includeInactive").prop("checked"),
        orderBy: orderBy,
        order: order,
        offset: init ? 0 : $("#addressList tr").length
      },
      success: function(data) {
        if (data.success) {
          var list = $("#addressList");
          if (init)
            list.empty();
          for (var i=0; i<data.address.length; i++) {
            list.append(createAddressRow(data.address[i]));
          }
          $("#continue").css("display", data.rest ? "inline" : "none");
        } else {
          alert(data.error)
        }
      },
      error: function() {
        alert("error")
      }
    });
  }

  function submitMemo(address, event) {
    event.preventDefault();
    $(this).find("button[type=submit]").prop("disabled", true);

    var newaddr = address;
    newaddr.memo = $(this).find("input").val();

    $.ajax({
      type: "PUT",
      url: "/address",
      data: newaddr,
      success: function(data) {
        if (data.success) {
          $("#"+address.address).replaceWith(createAddressRow(newaddr));
        } else {
          alert(data.error);
          $(this).find("button[type=submit]").prop("disabled", false);
        }
      },
      error: function() {
        alert("error");
        $(this).find("button[type=submit]").prop("disabled", false);
      },
    });
  }

  function activeAddress(address, event) {
    $(this).prop("disabled", true);
    
    var newaddr = address;
    newaddr.active = !newaddr.active;

    $.ajax({
      type: "PUT",
      url: "/address",
      data: newaddr,
      success: function(data) {
        if (data.success) {
          $("#"+address.address).replaceWith(createAddressRow(newaddr));
        } else {
          alert(data.error);
          $(this).prop("disabled", false);
        }
      },
      error: function() {
        alert("error");
        $(this).prop("disabled", false);
      },
    });

    $("#"+address.address).replaceWith(createAddressRow(address));
  }
  
  var orderBy = "modified_at";
  var order = "desc";
  
  function updateHeader() {
    var column = [
      ["address", "アドレス"],
      ["memo", "メモ"],
      ["created_at", "作成日時"],
      ["modified_at", "更新日時"],
      ["active", "削除"]
    ];
    
    var header = $("#addressListHeader");
    header.empty();
    for (var i=0; i<column.length; i++) {
      var a = $("<a href='#'>")
          .attr("id", column[i][0])
          .text(column[i][1]+" ")
          .click(sortColumn.bind(a, column[i][0]));
      if (orderBy == column[i][0]) {
        if (order == "desc") {
          a.append($("<span class='glyphicon glyphicon-chevron-down'>"));
        } else {
          a.append($("<span class='glyphicon glyphicon-chevron-up'>"));
        }
      }
      header.append($("<th>")
        .append(a));
    }
  }
  
  function sortColumn(column, event) {
    event.preventDefault();
    if (column == orderBy) {
      if (order == "desc")
        order = "asc";
      else
        order = "desc";
    } else {
      orderBy = column;
      order = "desc";
    }
    updateHeader();
    listAddress(true);
  }

  if ($("#addressListHeader").length>0) {
    updateHeader();
  }
  if ($("#addressList").length>0) {
    listAddress(true);
  }
  
  $("#filter").submit(function(event) {
    event.preventDefault();
    listAddress(true);
  });
  
  $("#includeInactive").change(function() {
    listAddress(true);
  });
  
  $("#continue").click(function() {
    listAddress(false);
  });
  
  $("#errorDialog").modal("show");
})
