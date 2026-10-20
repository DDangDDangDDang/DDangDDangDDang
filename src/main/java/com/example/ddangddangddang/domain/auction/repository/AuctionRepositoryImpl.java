package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionSearchCondition;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionListResponse;
import com.example.ddangddangddang.domain.auction.entity.AuctionSort;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.entity.QAuction;
import com.example.ddangddangddang.domain.category.entity.QCategory;
import com.example.ddangddangddang.domain.product.entity.QProduct;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import javax.swing.undo.AbstractUndoableEdit;
import java.util.List;

@RequiredArgsConstructor
public class AuctionRepositoryImpl implements AuctionRepositoryCustom{
    private final JPAQueryFactory queryFactory;
    @Override
    public Page<AuctionListResponse> search(AuctionSearchCondition condition, Pageable pageable) {
        QAuction auction = QAuction.auction;
        QProduct product = QProduct.product;
        QCategory category = QCategory.category;

        //목록 조회 : 필요한 데이터만 dot 받기
        List<AuctionListResponse> content =queryFactory
                .select(Projections.constructor(AuctionListResponse.class,auction.id,product.id,product.name,category.name,auction.currentPrice,auction.bidCount,auction.status,auction.endAt,auction.liveStream.isNotNull()))
                .from(auction)
                .join(auction.product,product)
                .join(product.category,category)
                //목록
                .where(
                        productNameContains(product, condition.productName()),
                        categoryEq(category,condition.categoryId()),
                        priceGoe(auction, condition.minPrice()),
                        priceLoe(auction, condition.maxPrice()),
                        statusEq(auction,condition.status()))
                .orderBy(orderBy(auction,condition.sort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> countQuery = queryFactory.select(auction.count())
                .from(auction)
                .join(auction.product, product)
                .join(product.category, category)
                //개수
                .where(
                        productNameContains(product, condition.productName()),
                        categoryEq(category, condition.categoryId()),
                        priceGoe(auction, condition.minPrice()),
                        priceLoe(auction, condition.maxPrice()),
                        statusEq(auction, condition.status())
                );

        return PageableExecutionUtils.getPage(content,pageable, countQuery::fetchOne);

    }

    private BooleanExpression productNameContains(QProduct product,String productName){
        if (productName == null || productName.isBlank()){
            return null;
        }
        return product.name.containsIgnoreCase(productName);
    }

    //카테고리
    private BooleanExpression categoryEq(QCategory category,Long categoryId){
        if (categoryId ==null){
            return null;
        }
        return category.id.eq(categoryId).or(category.parent.id.eq(categoryId));
    }

    //햔제가>=최소가
    private BooleanExpression priceGoe(QAuction auction,Long minPrice){
        return minPrice == null ? null : auction.currentPrice.goe(minPrice);
    }

    //햔제기<=최소가
    private BooleanExpression priceLoe(QAuction auction,Long maxPrice){
        return maxPrice == null ? null : auction.currentPrice.loe(maxPrice);
    }

    //경매 상태: 보낼시 그 상태만, 안보낼시 진행,대기중인 경매들만(종료,실패,취소 상태는 제외)
    private BooleanExpression statusEq(QAuction auction, AuctionStatus status){
        if (status == null){
            return auction.status.in(AuctionStatus.READY,AuctionStatus.ONGOING);
        }
        return auction.status.eq(status);
    }

    //정렬 : 안보낼시 기본 최신순,값이 같을 경우 id 순으로
    private OrderSpecifier<?>[] orderBy(QAuction auction, AuctionSort sort){
        if (sort ==null){
            sort = AuctionSort.LATEST;
        }
        return switch (sort){
            case LATEST -> new OrderSpecifier<?>[]{auction.createdAt.desc(),auction.id.desc()};
            case PRICE_ASC -> new OrderSpecifier<?>[]{auction.currentPrice.asc(),auction.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{auction.currentPrice.desc(),auction.id.desc()};
            case ENDING_SOON -> new OrderSpecifier<?>[]{auction.endAt.asc(),auction.id.desc()};
        };
    }
}
